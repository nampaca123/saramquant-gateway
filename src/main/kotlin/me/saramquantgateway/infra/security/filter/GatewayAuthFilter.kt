package me.saramquantgateway.infra.security.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class GatewayAuthFilter(
    @param:Value("\${app.gateway-auth-key}") private val expectedKey: String,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method == "OPTIONS" || !request.servletPath.startsWith("/api")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val provided = request.getHeader("X-Gateway-Auth-Key") ?: ""
        if (provided != expectedKey) {
            response.status = 403
            response.contentType = "application/json"
            response.writer.write("""{"error":"Forbidden"}""")
            return
        }
        chain.doFilter(request, response)
    }
}
