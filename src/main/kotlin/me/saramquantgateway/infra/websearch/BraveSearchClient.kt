package me.saramquantgateway.infra.websearch

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class BraveSearchClient(
    @Value("\${app.brave-search.api-key:}") private val apiKey: String,
) {
    private val client: WebClient = WebClient.builder()
        .baseUrl("https://api.search.brave.com")
        .build()

    fun search(query: String, count: Int = 6): String {
        if (apiKey.isBlank()) return """{"error": "Brave Search API key not configured"}"""

        val response = client.get()
            .uri { it.path("/res/v1/web/search").queryParam("q", query).queryParam("count", count).build() }
            .header("X-Subscription-Token", apiKey)
            .header("Accept", "application/json")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block(Duration.ofSeconds(10))
            ?: return """{"results": []}"""

        @Suppress("UNCHECKED_CAST")
        val results = (response["web"] as? Map<String, Any>)?.get("results") as? List<Map<String, Any>>
            ?: return """{"results": []}"""

        val simplified = results.take(count).map { r ->
            mapOf("title" to r["title"], "description" to r["description"], "url" to r["url"])
        }
        return com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(mapOf("results" to simplified))
    }
}
