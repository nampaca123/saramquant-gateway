package me.saramquantgateway.infra.auth.controller

import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.infra.auth.dto.ManualLoginRequest
import me.saramquantgateway.infra.auth.dto.ManualSignupRequest
import me.saramquantgateway.infra.auth.service.AuthService
import me.saramquantgateway.infra.oauth.lib.OAuthProperties
import me.saramquantgateway.infra.security.CookieUtil
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.util.UriComponentsBuilder
import java.util.UUID

@RestController
class AuthController(
    private val authService: AuthService,
    private val cookieUtil: CookieUtil,
    private val oauthProps: OAuthProperties,
    @param:Value("\${app.frontend-redirect-url}") private val frontendRedirectUrl: String,
) {

    // ── OAuth initiation ──

    @GetMapping("/oauth2/authorization/google")
    fun googleAuthorize(response: HttpServletResponse) {
        val url = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
            .queryParam("client_id", oauthProps.google.clientId)
            .queryParam("redirect_uri", oauthProps.google.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", "email profile")
            .build().toUriString()
        response.sendRedirect(url)
    }

    @GetMapping("/oauth2/authorization/kakao")
    fun kakaoAuthorize(response: HttpServletResponse) {
        val url = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
            .queryParam("client_id", oauthProps.kakao.clientId)
            .queryParam("redirect_uri", oauthProps.kakao.redirectUri)
            .queryParam("response_type", "code")
            .build().toUriString()
        response.sendRedirect(url)
    }

    // ── OAuth callbacks ──

    @GetMapping("/login/oauth2/code/google")
    fun googleCallback(@RequestParam code: String, response: HttpServletResponse) {
        handleOAuthCallback(AuthProvider.GOOGLE, code, response)
    }

    @GetMapping("/login/oauth2/code/kakao")
    fun kakaoCallback(@RequestParam code: String, response: HttpServletResponse) {
        handleOAuthCallback(AuthProvider.KAKAO, code, response)
    }

    // ── Manual (email) auth ──

    @PostMapping("/api/auth/signup")
    fun signup(
        @Valid @RequestBody req: ManualSignupRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Map<String, String>> {
        try {
            val result = authService.manualSignup(req)
            cookieUtil.setAccessToken(response, result.accessToken)
            cookieUtil.setRefreshToken(response, result.refreshToken)
            return ResponseEntity.ok().build()
        } catch (_: AuthService.AccountDeactivatedException) {
            return ResponseEntity.status(409)
                .body(mapOf("code" to "ACCOUNT_DEACTIVATED", "message" to "Account deactivated. Log in to reactivate."))
        } catch (_: AuthService.EmailAlreadyExistsException) {
            return ResponseEntity.status(409)
                .body(mapOf("code" to "EMAIL_EXISTS", "message" to "Email already in use."))
        }
    }

    @PostMapping("/api/auth/login")
    fun manualLogin(
        @Valid @RequestBody req: ManualLoginRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        val result = authService.manualLogin(req)
        cookieUtil.setAccessToken(response, result.accessToken)
        cookieUtil.setRefreshToken(response, result.refreshToken)
        return ResponseEntity.ok().build()
    }

    // ── Token management ──

    @PostMapping("/api/auth/refresh")
    fun refresh(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        val rawToken = cookieUtil.extractRefreshToken(request)
            ?: return ResponseEntity.status(401).build()

        try {
            val result = authService.refresh(rawToken)
            cookieUtil.setAccessToken(response, result.accessToken)
            cookieUtil.setRefreshToken(response, result.refreshToken)
            return ResponseEntity.ok().build()
        } catch (_: AuthService.AccountDeactivatedException) {
            cookieUtil.clearAll(response)
            return ResponseEntity.status(403).build()
        }
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

    private fun handleOAuthCallback(provider: AuthProvider, code: String, response: HttpServletResponse) {
        try {
            val result = authService.oauthLogin(provider, code)
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
