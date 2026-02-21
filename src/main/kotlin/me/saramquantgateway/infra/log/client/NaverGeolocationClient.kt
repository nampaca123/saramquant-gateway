package me.saramquantgateway.infra.log.client

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import java.time.Duration
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

@ConfigurationProperties(prefix = "app.naver-geo")
data class NaverGeoProperties(val accessKey: String, val secretKey: String)

data class GeoLookupResult(
    val country: String?,
    val region1: String?,
    val region2: String?,
    val region3: String?,
    val latitude: Double?,
    val longitude: Double?,
    val networkProvider: String?,
)

@Component
class NaverGeolocationClient(
    private val props: NaverGeoProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = WebClient.builder()
        .baseUrl("https://geolocation.apigw.ntruss.com")
        .build()

    fun lookup(ip: String): GeoLookupResult? {
        val path = "/geolocation/v2/geoLocation"
        val query = "ip=$ip&ext=t&enc=utf8&responseFormatType=json"
        val fullPath = "$path?$query"
        val timestamp = System.currentTimeMillis().toString()
        val signature = createSignature("GET", fullPath, timestamp)

        return try {
            val body = webClient.get()
                .uri("$fullPath")
                .header("x-ncp-apigw-timestamp", timestamp)
                .header("x-ncp-iam-access-key", props.accessKey)
                .header("x-ncp-apigw-signature-v2", signature)
                .retrieve()
                .bodyToMono(String::class.java)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(8)))
                .block(Duration.ofSeconds(15))

            parseResponse(body)
        } catch (e: Exception) {
            log.warn("[NaverGeo] lookup failed for ip={}: {}", ip, e.message)
            null
        }
    }

    private fun parseResponse(body: String?): GeoLookupResult? {
        if (body == null) return null
        val root = objectMapper.readTree(body)
        if (root.path("returnCode").asInt(-1) != 0) return null
        val geo = root.path("geoLocation")
        if (geo.isMissingNode) return null

        return GeoLookupResult(
            country = geo.textOrNull("country"),
            region1 = geo.textOrNull("r1"),
            region2 = geo.textOrNull("r2"),
            region3 = geo.textOrNull("r3"),
            latitude = geo.doubleOrNull("lat"),
            longitude = geo.doubleOrNull("long"),
            networkProvider = geo.textOrNull("net"),
        )
    }

    private fun createSignature(method: String, path: String, timestamp: String): String {
        val message = "$method $path\n$timestamp\n${props.accessKey}"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(props.secretKey.toByteArray(), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(message.toByteArray()))
    }

    private fun JsonNode.textOrNull(field: String): String? =
        path(field).takeIf { !it.isMissingNode && !it.isNull }?.asText()

    private fun JsonNode.doubleOrNull(field: String): Double? =
        path(field).takeIf { !it.isMissingNode && !it.isNull }?.asDouble()
}
