package me.saramquantgateway.infra.storage.lib

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "supabase.storage")
data class SupabaseStorageProperties(
    val url: String,
    val bucket: String,
    val secretKey: String,
)
