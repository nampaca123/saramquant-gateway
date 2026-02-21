package me.saramquantgateway.infra.log.util

import jakarta.servlet.http.HttpServletRequest

object ClientIpExtractor {

    private val HEADERS = listOf(
        "X-Forwarded-For",
        "X-Real-IP",
        "CF-Connecting-IP",
    )

    fun extract(request: HttpServletRequest): String {
        for (header in HEADERS) {
            val value = request.getHeader(header)
            if (!value.isNullOrBlank()) return value.split(",")[0].trim()
        }
        return request.remoteAddr ?: "unknown"
    }
}
