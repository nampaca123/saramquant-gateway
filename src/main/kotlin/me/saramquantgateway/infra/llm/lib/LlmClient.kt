package me.saramquantgateway.infra.llm.lib

interface LlmClient {
    fun complete(model: String, system: String, user: String): String
}
