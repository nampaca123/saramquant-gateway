package me.saramquantgateway.infra.log.util

object IpMasker {

    fun mask(ip: String): String {
        if (ip.contains(":")) {
            val parts = ip.split(":")
            if (parts.size < 4) return ip
            return parts.take(parts.size / 2).joinToString(":") + ":*:*"
        }
        val parts = ip.split(".")
        if (parts.size != 4) return ip
        return "${parts[0]}.${parts[1]}.*.*"
    }
}
