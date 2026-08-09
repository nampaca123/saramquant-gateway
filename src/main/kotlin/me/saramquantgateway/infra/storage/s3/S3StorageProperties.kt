package me.saramquantgateway.infra.storage.s3

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.s3")
data class S3StorageProperties(
    val bucket: String,
    val appPrefix: String,
    val region: String,
)
