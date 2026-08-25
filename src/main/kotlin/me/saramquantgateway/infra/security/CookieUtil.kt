package me.saramquantgateway.infra.security

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class CookieUtil(
    @param:Value("\${jwt.access-token-ttl}") private val accessTtl: Int,
    @param:Value("\${jwt.refresh-token-ttl}") private val refreshTtl: Int,
    @param:Value("\${cookie.secure}") private val secure: Boolean,
    // 비어 있으면 host-only 쿠키(로컬), 값이 있으면 서브도메인 공유(예: .saramquant.com)
    @param:Value("\${cookie.domain}") private val domain: String,
) {
    companion object {
        const val ACCESS_COOKIE = "__sq_at"
        const val REFRESH_COOKIE = "__sq_rt"
    }

    fun setAccessToken(response: HttpServletResponse, token: String) {
        response.addCookie(buildCookie(ACCESS_COOKIE, token, "/api", accessTtl))
    }

    fun setRefreshToken(response: HttpServletResponse, token: String) {
        response.addCookie(buildCookie(REFRESH_COOKIE, token, "/api/auth", refreshTtl))
    }

    fun clearAll(response: HttpServletResponse) {
        response.addCookie(buildCookie(ACCESS_COOKIE, "", "/api", 0))
        response.addCookie(buildCookie(REFRESH_COOKIE, "", "/api/auth", 0))
    }

    fun extractAccessToken(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == ACCESS_COOKIE }?.value

    fun extractRefreshToken(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == REFRESH_COOKIE }?.value

    private fun buildCookie(name: String, value: String, path: String, maxAge: Int) =
        Cookie(name, value).apply {
            this.path = path
            this.maxAge = maxAge
            this.isHttpOnly = true
            this.secure = this@CookieUtil.secure
            if (this@CookieUtil.domain.isNotBlank()) this.domain = this@CookieUtil.domain
            this.setAttribute("SameSite", "Lax")
        }
}
