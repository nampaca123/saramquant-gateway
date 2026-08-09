package me.saramquantgateway.infra.log.service

import me.saramquantgateway.domain.store.AuditLogStore
import me.saramquantgateway.domain.store.VisitorStat
import me.saramquantgateway.infra.log.dto.HourlyCount
import me.saramquantgateway.infra.log.dto.PathCount
import me.saramquantgateway.infra.log.dto.VisitCluster
import me.saramquantgateway.infra.log.dto.VisitStatsResponse
import me.saramquantgateway.infra.log.dto.VisitSummary
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class VisitStatsService(
    private val store: AuditLogStore,
) {

    fun getStats(startDate: LocalDate?, endDate: LocalDate?): VisitStatsResponse {
        val (from, to) = AuditLogRange.resolve(startDate, endDate)

        val stats = store.countVisitors(from, to)
        val totalVisits = visitorCount(stats)

        return VisitStatsResponse(
            clusters = clusters(totalVisits),
            hourlyDistribution = hourly(stats),
            pathDistribution = paths(stats),
            summary = VisitSummary(
                totalCountries = if (totalVisits > 0) 1 else 0,
                totalRegions = if (totalVisits > 0) 1 else 0,
                totalVisits = totalVisits,
            ),
            filter = mapOf("startDate" to startDate?.toString(), "endDate" to endDate?.toString()),
        )
    }

    // geolocation 제거로 위치 정보는 없어 단일 UNKNOWN 클러스터로 응답 형태만 유지한다.
    private fun clusters(totalVisits: Int): List<VisitCluster> =
        if (totalVisits == 0) emptyList()
        else listOf(VisitCluster("UNKNOWN", "UNKNOWN", "", 0.0, 0.0, totalVisits))

    private fun visitorCount(stats: List<VisitorStat>): Int =
        stats.map { it.visitDate to it.ipMasked }.distinct().size

    private fun hourly(stats: List<VisitorStat>): List<HourlyCount> {
        val counts = stats.groupBy { it.hour }.mapValues { (_, rows) -> visitorCount(rows) }
        return (0..23).map { HourlyCount(it, counts.getOrDefault(it, 0)) }
    }

    private fun paths(stats: List<VisitorStat>): List<PathCount> =
        stats.filter { it.path != null }
            .groupBy { it.path!! }
            .map { (path, rows) -> PathCount(path, visitorCount(rows)) }
            .sortedByDescending { it.count }
            .take(20)
}
