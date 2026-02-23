package me.saramquantgateway.infra.llm.lib

import me.saramquantgateway.infra.llm.config.LlmProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class AnthropicClient(private val props: LlmProperties) : LlmClient {

    private val client: WebClient = WebClient.builder()
        .baseUrl("https://api.anthropic.com")
        .defaultHeader("x-api-key", props.claude.apiKey)
        .defaultHeader("anthropic-version", "2023-06-01")
        .build()

    override fun complete(model: String, system: String, user: String): String {
        val body = mapOf(
            "model" to model,
            "max_tokens" to 1024,
            "system" to system,
            "messages" to listOf(mapOf("role" to "user", "content" to user)),
        )

        val response = client.post()
            .uri("/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block(props.claude.timeout)
            ?: throw RuntimeException("Anthropic API returned empty response")

        @Suppress("UNCHECKED_CAST")
        val content = response["content"] as? List<Map<String, Any>>
            ?: throw RuntimeException("Anthropic API: missing content field")
        return content.first()["text"] as? String
            ?: throw RuntimeException("Anthropic API: missing text in content")
    }
}
