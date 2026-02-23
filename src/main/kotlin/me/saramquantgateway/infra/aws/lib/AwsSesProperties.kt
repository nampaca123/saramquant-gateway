package me.saramquantgateway.infra.aws.lib

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "aws.ses")
data class AwsSesProperties(
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val senderEmail: String,
)
