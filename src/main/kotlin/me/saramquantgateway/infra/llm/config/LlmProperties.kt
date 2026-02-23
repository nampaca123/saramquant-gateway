package me.saramquantgateway.infra.llm.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.llm")
data class LlmProperties(
    val claude: ProviderConfig,
    val openai: ProviderConfig,
    val stockModel: String = "claude-sonnet-4-6",
    val portfolioModel: String = "claude-opus-4-6",
    val fallbackModel: String = "gpt-5.2",
    val dailyLimit: Int = 20,
    val maxRetries: Int = 3,
    val totalTimeout: Duration = Duration.ofSeconds(90),
) {
    data class ProviderConfig(
        val apiKey: String,
        val timeout: Duration = Duration.ofSeconds(30),
    )
}
