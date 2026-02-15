package me.saramquantgateway.infra.oauth.dto

data class OAuthUserInfo(
    val email: String,
    val name: String,
    val providerId: String,
    val imageUrl: String?,
)
