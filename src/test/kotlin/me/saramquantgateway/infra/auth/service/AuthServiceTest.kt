package me.saramquantgateway.infra.auth.service

import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.infra.auth.dto.ManualSignupRequest
import me.saramquantgateway.infra.jwt.lib.JwtProvider
import me.saramquantgateway.infra.jwt.service.RefreshTokenService
import me.saramquantgateway.infra.oauth.dto.OAuthTokenResponse
import me.saramquantgateway.infra.oauth.dto.OAuthUserInfo
import me.saramquantgateway.infra.oauth.lib.GoogleOAuthClient
import me.saramquantgateway.infra.oauth.lib.KakaoOAuthClient
import me.saramquantgateway.infra.storage.service.ProfileImageService
import me.saramquantgateway.infra.systememail.service.EmailVerificationService
import me.saramquantgateway.infra.systememail.service.SystemEmailService
import me.saramquantgateway.infra.user.service.ProfileService
import me.saramquantgateway.infra.user.service.UserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

class AuthServiceTest {

    private lateinit var userService: UserService
    private lateinit var profileService: ProfileService
    private lateinit var jwtProvider: JwtProvider
    private lateinit var refreshTokenService: RefreshTokenService
    private lateinit var profileImageService: ProfileImageService
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var googleClient: GoogleOAuthClient
    private lateinit var kakaoClient: KakaoOAuthClient
    private lateinit var systemEmailService: SystemEmailService
    private lateinit var emailVerificationService: EmailVerificationService
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        userService = Mockito.mock(UserService::class.java)
        profileService = Mockito.mock(ProfileService::class.java)
        jwtProvider = Mockito.mock(JwtProvider::class.java)
        refreshTokenService = Mockito.mock(RefreshTokenService::class.java)
        profileImageService = Mockito.mock(ProfileImageService::class.java)
        passwordEncoder = Mockito.mock(PasswordEncoder::class.java)
        googleClient = Mockito.mock(GoogleOAuthClient::class.java)
        kakaoClient = Mockito.mock(KakaoOAuthClient::class.java)
        systemEmailService = Mockito.mock(SystemEmailService::class.java)
        emailVerificationService = Mockito.mock(EmailVerificationService::class.java)

        authService = AuthService(
            userService, profileService, jwtProvider, refreshTokenService, profileImageService,
            passwordEncoder, googleClient, kakaoClient, systemEmailService, emailVerificationService,
        )
    }

    @Test
    fun `manualSignup rejects an existing active email`() {
        Mockito.`when`(userService.findByEmail("dup@example.com")).thenReturn(user("dup@example.com"))

        assertThrows(AuthService.EmailAlreadyExistsException::class.java) {
            authService.manualSignup(signupRequest("dup@example.com"))
        }
        Mockito.verify(userService, Mockito.never()).createManualUser(anyString(), anyString(), anyString())
    }

    @Test
    fun `manualSignup rejects a deactivated email`() {
        Mockito.`when`(userService.findByEmail("gone@example.com"))
            .thenReturn(user("gone@example.com").apply { isActive = false })

        assertThrows(AuthService.AccountDeactivatedException::class.java) {
            authService.manualSignup(signupRequest("gone@example.com"))
        }
    }

    @Test
    fun `manualSignup creates the user before saving the refresh token`() {
        val created = user("new@example.com")
        stubTokens(created)
        Mockito.`when`(userService.findByEmail("new@example.com")).thenReturn(null)
        Mockito.`when`(passwordEncoder.encode("password123")).thenReturn("bcrypt")
        Mockito.`when`(userService.createManualUser("new@example.com", "New", "bcrypt")).thenReturn(created)

        val result = authService.manualSignup(signupRequest("new@example.com"))

        assertEquals("access", result.accessToken)
        assertEquals(created, result.user)
        val order = Mockito.inOrder(userService, refreshTokenService)
        order.verify(userService).createManualUser("new@example.com", "New", "bcrypt")
        order.verify(refreshTokenService).save(created.id, "refresh")
    }

    @Test
    fun `oauthLogin rejects an email registered with another provider`() {
        stubGoogle("mixed@example.com")
        Mockito.`when`(userService.findByEmail("mixed@example.com"))
            .thenReturn(user("mixed@example.com", AuthProvider.KAKAO))

        assertThrows(AuthService.DuplicateEmailException::class.java) {
            authService.oauthLogin(AuthProvider.GOOGLE, "code")
        }
    }

    @Test
    fun `oauthLogin reactivates a deactivated account`() {
        stubGoogle("back@example.com")
        val existing = user("back@example.com", AuthProvider.GOOGLE).apply { isActive = false }
        stubTokens(existing)
        Mockito.`when`(userService.findByEmail("back@example.com")).thenReturn(existing)

        authService.oauthLogin(AuthProvider.GOOGLE, "code")

        Mockito.verify(userService).reactivateUser(existing)
        Mockito.verify(userService).updateLastLogin(existing.id)
        Mockito.verify(systemEmailService).sendReactivationEmail(existing)
        Mockito.verify(userService, Mockito.never())
            .createOAuthUser(userInfo("back@example.com"), AuthProvider.GOOGLE)
    }

    @Test
    fun `oauthLogin creates a new user and sends the welcome email`() {
        stubGoogle("fresh@example.com")
        val created = user("fresh@example.com", AuthProvider.GOOGLE)
        stubTokens(created)
        Mockito.`when`(userService.findByEmail("fresh@example.com")).thenReturn(null)
        Mockito.`when`(userService.createOAuthUser(userInfo("fresh@example.com"), AuthProvider.GOOGLE))
            .thenReturn(created)

        val result = authService.oauthLogin(AuthProvider.GOOGLE, "code")

        assertEquals(created, result.user)
        val order = Mockito.inOrder(userService, refreshTokenService)
        order.verify(userService).createOAuthUser(userInfo("fresh@example.com"), AuthProvider.GOOGLE)
        order.verify(refreshTokenService).save(created.id, "refresh")
        Mockito.verify(systemEmailService).sendWelcomeEmail(created)
    }

    private fun stubTokens(u: UserDoc) {
        Mockito.`when`(jwtProvider.generateAccessToken(u.id, u.email, u.provider, u.role)).thenReturn("access")
        Mockito.`when`(jwtProvider.generateRefreshToken(u.id)).thenReturn("refresh")
    }

    private fun stubGoogle(email: String) {
        Mockito.`when`(googleClient.exchangeCode("code")).thenReturn(OAuthTokenResponse("token"))
        Mockito.`when`(googleClient.getUserInfo("token")).thenReturn(userInfo(email))
    }

    private fun userInfo(email: String) =
        OAuthUserInfo(email = email, name = "New", providerId = "pid", imageUrl = null)

    private fun signupRequest(email: String) =
        ManualSignupRequest(email = email, password = "password123", name = "New", verificationId = UUID.randomUUID())

    private fun user(email: String, provider: AuthProvider = AuthProvider.MANUAL) = UserDoc(
        email = email,
        emailHash = "hash-$email",
        name = "New",
        provider = provider,
        providerId = email,
    )
}
