package me.saramquantgateway.infra.user.controller

import me.saramquantgateway.domain.enum.OAuthProvider
import me.saramquantgateway.infra.jwt.service.RefreshTokenService
import me.saramquantgateway.infra.security.CookieUtil
import me.saramquantgateway.infra.user.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class AuthController(
    private val authService: AuthService,
    private val cookieUtil: CookieUtil,
    @param:Value("\${app.frontend-redirect-url}") private val frontendRedirectUrl: String,
) {

    @GetMapping("/login/oauth2/code/google")
    fun googleCallback(
        @RequestParam code: String,
        response: HttpServletResponse,
    ) {
        handleCallback(OAuthProvider.GOOGLE, code, response)
    }

    @GetMapping("/login/oauth2/code/kakao")
    fun kakaoCallback(
        @RequestParam code: String,
        response: HttpServletResponse,
    ) {
        handleCallback(OAuthProvider.KAKAO, code, response)
    }

    @PostMapping("/api/auth/refresh")
    fun refresh(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        val rawToken = cookieUtil.extractRefreshToken(request)
            ?: return ResponseEntity.status(401).build()

        val result = authService.refresh(rawToken)
        cookieUtil.setAccessToken(response, result.accessToken)
        cookieUtil.setRefreshToken(response, result.refreshToken)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/api/auth/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        cookieUtil.extractRefreshToken(request)?.let {
            try { authService.logout(it) } catch (_: Exception) {}
        }
        cookieUtil.clearAll(response)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/api/auth/logout-all")
    fun logoutAll(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        val userId = request.userPrincipal?.name?.let { UUID.fromString(it) }
            ?: return ResponseEntity.status(401).build()
        authService.logoutAll(userId)
        cookieUtil.clearAll(response)
        return ResponseEntity.noContent().build()
    }

    private fun handleCallback(provider: OAuthProvider, code: String, response: HttpServletResponse) {
        try {
            val result = authService.login(provider, code)
            cookieUtil.setAccessToken(response, result.accessToken)
            cookieUtil.setRefreshToken(response, result.refreshToken)
            response.sendRedirect(frontendRedirectUrl)
        } catch (e: AuthService.DuplicateEmailException) {
            response.sendRedirect(
                "$frontendRedirectUrl?error=DUPLICATE_EMAIL&existing_provider=${e.existingProvider}"
            )
        } catch (_: Exception) {
            response.sendRedirect("$frontendRedirectUrl?error=OAUTH_FAILED&provider=$provider")
        }
    }
}
