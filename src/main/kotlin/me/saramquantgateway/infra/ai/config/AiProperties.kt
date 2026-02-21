package me.saramquantgateway.infra.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.ai")
data class AiProperties(
    val claude: ProviderConfig,
    val openai: ProviderConfig,
    val stockModel: String = "claude-sonnet-4-6-20260217",
    val portfolioModel: String = "claude-opus-4-6-20260210",
    val fallbackModel: String = "gpt-5.2",
    val dailyLimit: Int = 20,
    val maxRetries: Int = 3,
) {
    data class ProviderConfig(
        val apiKey: String,
        val timeout: Duration = Duration.ofSeconds(30),
    )
}
