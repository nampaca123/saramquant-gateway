package me.saramquantgateway.infra.auth.service

import me.saramquantgateway.domain.entity.user.User
import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.infra.auth.dto.ManualLoginRequest
import me.saramquantgateway.infra.auth.dto.ManualSignupRequest
import me.saramquantgateway.infra.jwt.lib.JwtProvider
import me.saramquantgateway.infra.jwt.service.RefreshTokenService
import me.saramquantgateway.infra.oauth.lib.GoogleOAuthClient
import me.saramquantgateway.infra.oauth.lib.KakaoOAuthClient
import me.saramquantgateway.infra.oauth.lib.OAuthClient
import me.saramquantgateway.infra.storage.service.ProfileImageService
import me.saramquantgateway.infra.user.service.ProfileService
import me.saramquantgateway.infra.user.service.UserService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AuthService(
    private val userService: UserService,
    private val profileService: ProfileService,
    private val jwtProvider: JwtProvider,
    private val refreshTokenService: RefreshTokenService,
    private val profileImageService: ProfileImageService,
    private val passwordEncoder: PasswordEncoder,
    private val googleClient: GoogleOAuthClient,
    private val kakaoClient: KakaoOAuthClient,
) {

    @Transactional
    fun oauthLogin(provider: AuthProvider, code: String): AuthResult {
        val client: OAuthClient = when (provider) {
            AuthProvider.GOOGLE -> googleClient
            AuthProvider.KAKAO  -> kakaoClient
            AuthProvider.MANUAL -> throw IllegalArgumentException("Use manual login")
        }

        val tokenRes = client.exchangeCode(code)
        val userInfo = client.getUserInfo(tokenRes.accessToken)

        val existing = userService.findByEmail(userInfo.email)
        val user: User

        if (existing != null) {
            if (existing.provider != provider) {
                throw DuplicateEmailException(existing.provider)
            }
            if (!existing.isActive) userService.reactivateUser(existing)
            user = existing
            userService.updateLastLogin(user.id)
        } else {
            user = userService.createOAuthUser(userInfo, provider)
            userInfo.imageUrl?.let { url ->
                val bucketUrl = profileImageService.uploadFromUrl(user.id, url)
                bucketUrl?.let { profileService.updateImageUrl(user.id, it) }
            }
        }

        return issueTokens(user)
    }

    @Transactional
    fun manualSignup(req: ManualSignupRequest): AuthResult {
        val existing = userService.findByEmail(req.email)
        if (existing != null) {
            if (!existing.isActive) throw AccountDeactivatedException()
            throw EmailAlreadyExistsException()
        }
        val hash = passwordEncoder.encode(req.password)!!
        val user = userService.createManualUser(req.email, req.name, hash)
        return issueTokens(user)
    }

    fun manualLogin(req: ManualLoginRequest): AuthResult {
        val user = userService.findByEmail(req.email) ?: throw InvalidCredentialsException()

        if (user.provider != AuthProvider.MANUAL || user.passwordHash == null) {
            throw InvalidCredentialsException()
        }
        if (!passwordEncoder.matches(req.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }

        if (!user.isActive) userService.reactivateUser(user)
        userService.updateLastLogin(user.id)
        return issueTokens(user)
    }

    fun refresh(rawRefreshToken: String): AuthResult {
        val claims = jwtProvider.validateToken(rawRefreshToken)
            ?: throw RefreshTokenService.InvalidRefreshTokenException()
        val userId = UUID.fromString(claims.subject)
        val user = userService.findById(userId) ?: throw RefreshTokenService.InvalidRefreshTokenException()

        if (!user.isActive) throw AccountDeactivatedException()

        val newRefreshToken = refreshTokenService.rotate(rawRefreshToken)
        val newAccessToken = jwtProvider.generateAccessToken(user.id, user.email, user.provider, user.role)
        return AuthResult(newAccessToken, newRefreshToken, null)
    }

    fun logout(rawRefreshToken: String) = refreshTokenService.revoke(rawRefreshToken)

    fun logoutAll(userId: UUID) = refreshTokenService.revokeAll(userId)

    private fun issueTokens(user: User): AuthResult {
        val accessToken = jwtProvider.generateAccessToken(user.id, user.email, user.provider, user.role)
        val refreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenService.save(user.id, refreshToken)
        return AuthResult(accessToken, refreshToken, user)
    }

    data class AuthResult(val accessToken: String, val refreshToken: String, val user: User?)

    class DuplicateEmailException(val existingProvider: AuthProvider) :
        RuntimeException("Email already registered with $existingProvider")

    class EmailAlreadyExistsException : RuntimeException("Email already in use")
    class InvalidCredentialsException : RuntimeException("Invalid email or password")
    class AccountDeactivatedException : RuntimeException("Account is deactivated. Please log in to reactivate.")
}
