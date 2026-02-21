package me.saramquantgateway.infra.security.filter

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.saramquantgateway.infra.log.util.ClientIpExtractor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class RateLimitFilter : OncePerRequestFilter() {

    private val buckets = ConcurrentHashMap<String, TimestampedBucket>()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val ip = ClientIpExtractor.extract(request)
        val bucket = buckets.computeIfAbsent(ip) { TimestampedBucket(createBucket()) }.also {
            it.lastAccess = System.currentTimeMillis()
        }

        if (bucket.bucket.tryConsume(1)) {
            chain.doFilter(request, response)
        } else {
            response.status = 429
            response.setHeader("Retry-After", "1")
            response.contentType = "application/json"
            response.writer.write("""{"error":"Too many requests"}""")
        }
    }

    @Scheduled(fixedRate = 3600_000)
    fun cleanup() {
        val cutoff = System.currentTimeMillis() - 3600_000
        buckets.entries.removeIf { it.value.lastAccess < cutoff }
    }

    private fun createBucket(): Bucket =
        Bucket.builder()
            .addLimit(Bandwidth.builder().capacity(10).refillGreedy(5, Duration.ofSeconds(1)).build())
            .build()

    private data class TimestampedBucket(val bucket: Bucket, var lastAccess: Long = System.currentTimeMillis())
}
