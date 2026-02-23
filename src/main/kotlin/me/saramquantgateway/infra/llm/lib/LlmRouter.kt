package me.saramquantgateway.infra.llm.lib

import me.saramquantgateway.infra.llm.config.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class LlmRouter(
    private val props: LlmProperties,
    private val anthropic: AnthropicClient,
    private val openai: OpenAiClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun complete(model: String, system: String, user: String): String {
        val deadline = Instant.now().plus(props.totalTimeout)
        val isClaudeModel = model.startsWith("claude")
        val primary: LlmClient = if (isClaudeModel) anthropic else openai

        for (attempt in 1..props.maxRetries) {
            val remaining = Duration.between(Instant.now(), deadline)
            if (remaining.isNegative || remaining.isZero) {
                log.warn("[LlmRouter] deadline exceeded before attempt {}, aborting", attempt)
                break
            }

            try {
                val currentModel = if (attempt < props.maxRetries) model else props.fallbackModel
                val client = if (attempt < props.maxRetries) primary else fallbackClient(primary)
                return client.complete(currentModel, system, user)
            } catch (e: Exception) {
                log.warn("[LlmRouter] attempt {}/{} failed: {}", attempt, props.maxRetries, e.message)
                if (attempt < props.maxRetries) {
                    val backoffMs = (1L shl attempt) * 1000
                    val remainingMs = Duration.between(Instant.now(), deadline).toMillis()
                    if (remainingMs <= 0) break
                    Thread.sleep(minOf(backoffMs, remainingMs))
                }
            }
        }
        throw RuntimeException("All LLM attempts failed (deadline: ${props.totalTimeout})")
    }

    private fun fallbackClient(primary: LlmClient): LlmClient =
        if (primary is AnthropicClient) openai else anthropic
}
