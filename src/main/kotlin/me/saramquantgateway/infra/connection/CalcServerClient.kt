package me.saramquantgateway.infra.connection

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@Component
class CalcServerClient(
    private val props: CalcServerProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient = RestClient.builder()
        .baseUrl(props.url)
        .defaultHeader("x-api-key", props.authKey)
        .build()

    fun get(path: String, params: Map<String, String> = emptyMap()): Map<*, *>? =
        try {
            val uri = buildUri(path, params)
            client.get()
                .uri(uri)
                .retrieve()
                .body(Map::class.java)
        } catch (e: RestClientResponseException) {
            log.warn("[CalcServerClient] GET {} → {} {}", path, e.statusCode, e.statusText)
            parseErrorBody(e)
        } catch (e: Exception) {
            log.error("[CalcServerClient] GET {} failed: {}", path, e.message)
            null
        }

    fun post(path: String, body: Any? = null, params: Map<String, String> = emptyMap()): Map<*, *>? =
        try {
            val uri = buildUri(path, params)
            val req = client.post().uri(uri)
            if (body != null) {
                req.contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
            }
            req.retrieve().body(Map::class.java)
        } catch (e: RestClientResponseException) {
            log.warn("[CalcServerClient] POST {} → {} {}", path, e.statusCode, e.statusText)
            parseErrorBody(e)
        } catch (e: Exception) {
            log.error("[CalcServerClient] POST {} failed: {}", path, e.message)
            null
        }

    private fun parseErrorBody(e: RestClientResponseException): Map<*, *>? =
        try {
            objectMapper.readValue(e.responseBodyAsString, Map::class.java)
        } catch (_: Exception) {
            mapOf("error" to e.statusText.ifEmpty { "Calc server error" })
        }

    private fun buildUri(path: String, params: Map<String, String>): String {
        if (params.isEmpty()) return path
        val qs = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$path?$qs"
    }
}
