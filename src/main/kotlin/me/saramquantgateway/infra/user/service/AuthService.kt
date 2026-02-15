package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.entity.User
import me.saramquantgateway.domain.enum.OAuthProvider
import me.saramquantgateway.infra.jwt.lib.JwtProvider
import me.saramquantgateway.infra.jwt.service.RefreshTokenService
import me.saramquantgateway.infra.oauth.lib.GoogleOAuthClient
import me.saramquantgateway.infra.oauth.lib.KakaoOAuthClient
import me.saramquantgateway.infra.oauth.lib.OAuthClient
import me.saramquantgateway.infra.storage.service.ProfileImageService
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
    private val googleClient: GoogleOAuthClient,
    private val kakaoClient: KakaoOAuthClient,
) {

    @Transactional
    fun login(provider: OAuthProvider, code: String): AuthResult {
        val client: OAuthClient = when (provider) {
            OAuthProvider.GOOGLE -> googleClient
            OAuthProvider.KAKAO  -> kakaoClient
        }

        val tokenRes = client.exchangeCode(code)
        val userInfo = client.getUserInfo(tokenRes.accessToken)

        val existing = userService.findByEmail(userInfo.email)
        val user: User

        if (existing != null) {
            if (existing.provider != provider) {
                throw DuplicateEmailException(existing.provider)
            }
            user = existing
            userService.updateLastLogin(user.id)
        } else {
            user = userService.createUser(userInfo, provider)
            userInfo.imageUrl?.let { url ->
                val bucketUrl = profileImageService.uploadFromUrl(user.id, url)
                bucketUrl?.let { profileService.updateImageUrl(user.id, it) }
            }
        }

        val accessToken = jwtProvider.generateAccessToken(user.id, user.email, user.provider)
        val refreshToken = jwtProvider.generateRefreshToken(user.id)
        refreshTokenService.save(user.id, refreshToken)

        return AuthResult(accessToken, refreshToken, user)
    }

    fun refresh(rawRefreshToken: String): AuthResult {
        val claims = jwtProvider.validateToken(rawRefreshToken)
            ?: throw RefreshTokenService.InvalidRefreshTokenException()
        val userId = UUID.fromString(claims.subject)
        val user = userService.findById(userId) ?: throw RefreshTokenService.InvalidRefreshTokenException()

        val newRefreshToken = refreshTokenService.rotate(rawRefreshToken)
        val newAccessToken = jwtProvider.generateAccessToken(user.id, user.email, user.provider)

        return AuthResult(newAccessToken, newRefreshToken, null)
    }

    fun logout(rawRefreshToken: String) {
        refreshTokenService.revoke(rawRefreshToken)
    }

    fun logoutAll(userId: UUID) {
        refreshTokenService.revokeAll(userId)
    }

    data class AuthResult(val accessToken: String, val refreshToken: String, val user: User?)
    class DuplicateEmailException(val existingProvider: OAuthProvider) :
        RuntimeException("Email already registered with $existingProvider")
}
