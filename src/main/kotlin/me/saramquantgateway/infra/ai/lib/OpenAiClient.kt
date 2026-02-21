package me.saramquantgateway.infra.ai.lib

import me.saramquantgateway.infra.ai.config.AiProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class OpenAiClient(private val props: AiProperties) : LlmClient {

    private val client: WebClient = WebClient.builder()
        .baseUrl("https://api.openai.com")
        .defaultHeader("Authorization", "Bearer ${props.openai.apiKey}")
        .build()

    override fun complete(model: String, system: String, user: String): String {
        val body = mapOf(
            "model" to model,
            "max_completion_tokens" to 2048,
            "messages" to listOf(
                mapOf("role" to "system", "content" to system),
                mapOf("role" to "user", "content" to user),
            ),
        )

        val response = client.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(Map::class.java)
            .block(props.openai.timeout)
            ?: throw RuntimeException("OpenAI API returned empty response")

        @Suppress("UNCHECKED_CAST")
        val choices = response["choices"] as? List<Map<String, Any>>
            ?: throw RuntimeException("OpenAI API: missing choices field")
        @Suppress("UNCHECKED_CAST")
        val message = choices.first()["message"] as? Map<String, Any>
            ?: throw RuntimeException("OpenAI API: missing message in choice")
        return message["content"] as? String
            ?: throw RuntimeException("OpenAI API: missing content in message")
    }
}
