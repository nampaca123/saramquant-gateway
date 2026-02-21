package me.saramquantgateway.infra.log.service

import me.saramquantgateway.infra.log.client.GeoLookupResult
import me.saramquantgateway.infra.log.client.NaverGeolocationClient
import me.saramquantgateway.infra.log.entity.IpGeolocation
import me.saramquantgateway.infra.log.repository.IpGeolocationRepository
import me.saramquantgateway.infra.log.util.InfraTrafficFilter
import me.saramquantgateway.infra.log.util.IpMasker
import me.saramquantgateway.infra.security.crypto.Hasher
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IpGeolocationService(
    private val repo: IpGeolocationRepository,
    private val naverClient: NaverGeolocationClient,
    private val hasher: Hasher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resolveId(ip: String): UUID? {
        if (ip == "unknown" || ip.isBlank()) return null
        val ipHash = hasher.hash(ip)
        val existing = repo.findByIpHash(ipHash)
        if (existing != null) {
            return if (InfraTrafficFilter.isInfraTraffic(existing.networkProvider)) null else existing.id
        }
        return null
    }

    @Async
    fun resolveAndBackfill(ip: String, auditLogId: UUID) {
        if (ip == "unknown" || ip.isBlank()) return
        val ipHash = hasher.hash(ip)
        val existing = repo.findByIpHash(ipHash)
        if (existing != null) return

        val geo = naverClient.lookup(ip) ?: return
        if (!hasValidGeoData(geo)) return
        if (InfraTrafficFilter.isInfraTraffic(geo.networkProvider)) return

        try {
            val saved = repo.save(
                IpGeolocation(
                    ipHash = ipHash,
                    ipMasked = IpMasker.mask(ip),
                    country = geo.country,
                    region1 = geo.region1,
                    region2 = geo.region2,
                    region3 = geo.region3,
                    latitude = geo.latitude,
                    longitude = geo.longitude,
                    networkProvider = geo.networkProvider,
                )
            )
            log.debug("[IpGeo] saved new geolocation id={} for ipHash={}", saved.id, ipHash)
        } catch (e: Exception) {
            log.warn("[IpGeo] upsert failed for ipHash={}: {}", ipHash, e.message)
        }
    }

    private fun hasValidGeoData(geo: GeoLookupResult): Boolean =
        (geo.latitude != null && geo.longitude != null) || !geo.region1.isNullOrBlank()
}
