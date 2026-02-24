package me.saramquantgateway.infra.security.filter

import me.saramquantgateway.infra.jwt.lib.JwtProvider
import me.saramquantgateway.infra.security.CookieUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtProvider: JwtProvider,
    private val cookieUtil: CookieUtil,
) : OncePerRequestFilter() {

    private val pathMatcher = AntPathMatcher()
    private val skipPaths = listOf(
        "/oauth2/authorization/**",
        "/login/oauth2/code/**",
        "/api/auth/signup",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/logout",
    )

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        skipPaths.any { pathMatcher.match(it, request.servletPath) }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val token = cookieUtil.extractAccessToken(request)

        if (token != null) {
            val claims = jwtProvider.validateToken(token)
            if (claims != null) {
                val role = claims["role"] as? String ?: "STANDARD"
                val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))
                val auth = UsernamePasswordAuthenticationToken(claims.subject, null, authorities)
                SecurityContextHolder.getContext().authentication = auth
            }
        }

        chain.doFilter(request, response)
    }
}
