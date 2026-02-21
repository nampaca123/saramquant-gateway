package me.saramquantgateway.infra.log.util

object InfraTrafficFilter {

    private val INFRA_PATTERNS = listOf(
        "AMAZON", "MICROSOFT-CORP", "GOOGLE-CLOUD",
        "DIGITALOCEAN", "HETZNER", "OVH",
        "DATACAMP", "AHREFS", "SEMRUSH", "BYTEDANCE",
    )

    fun isInfraTraffic(networkProvider: String?): Boolean {
        if (networkProvider.isNullOrBlank()) return false
        val upper = networkProvider.uppercase()
        return INFRA_PATTERNS.any { upper.contains(it) }
    }
}
