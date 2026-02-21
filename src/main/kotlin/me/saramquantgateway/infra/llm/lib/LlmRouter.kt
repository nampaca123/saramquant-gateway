package me.saramquantgateway.infra.llm.lib

import me.saramquantgateway.infra.llm.config.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LlmRouter(
    private val props: LlmProperties,
    private val anthropic: AnthropicClient,
    private val openai: OpenAiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun complete(model: String, system: String, user: String): String {
        val isClaudeModel = model.startsWith("claude")
        val primary: LlmClient = if (isClaudeModel) anthropic else openai

        for (attempt in 1..props.maxRetries) {
            try {
                val currentModel = if (attempt < props.maxRetries) model else props.fallbackModel
                val client = if (attempt < props.maxRetries) primary else fallbackClient(primary)
                return client.complete(currentModel, system, user)
            } catch (e: Exception) {
                log.warn("[LlmRouter] attempt {}/{} failed: {}", attempt, props.maxRetries, e.message)
                if (attempt < props.maxRetries) {
                    Thread.sleep((1L shl attempt) * 1000)
                }
            }
        }
        throw RuntimeException("All LLM attempts failed")
    }

    private fun fallbackClient(primary: LlmClient): LlmClient =
        if (primary is AnthropicClient) openai else anthropic
}
