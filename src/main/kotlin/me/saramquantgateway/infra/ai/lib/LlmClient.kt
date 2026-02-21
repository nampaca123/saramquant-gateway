package me.saramquantgateway.infra.ai.lib

interface LlmClient {
    fun complete(model: String, system: String, user: String): String
}
