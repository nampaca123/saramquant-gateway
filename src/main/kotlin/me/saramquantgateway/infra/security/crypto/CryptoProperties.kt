package me.saramquantgateway.infra.security.crypto

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.crypto")
data class CryptoProperties(val hashSecret: String)
