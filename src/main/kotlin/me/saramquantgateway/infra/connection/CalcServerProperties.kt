package me.saramquantgateway.infra.connection

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.calc-server")
data class CalcServerProperties(
    val url: String,
    val authKey: String,
    val timeoutMs: Long = 30000,
)
