package me.saramquantgateway.infra.oauth.lib

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oauth")
data class OAuthProperties(
    val google: ProviderConfig,
    val kakao: ProviderConfig,
) {
    data class ProviderConfig(
        val clientId: String,
        val clientSecret: String = "",
        val redirectUri: String,
    )
}
