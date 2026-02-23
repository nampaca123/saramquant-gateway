package me.saramquantgateway.infra.log.service

import me.saramquantgateway.infra.log.dto.VisitStatsResponse
import me.saramquantgateway.infra.log.dto.VisitCluster
import me.saramquantgateway.infra.log.dto.HourlyCount
import me.saramquantgateway.infra.log.dto.PathCount
import me.saramquantgateway.infra.log.dto.VisitSummary
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class VisitStatsService(
    private val em: EntityManager,
) {

    fun getStats(startDate: LocalDate?, endDate: LocalDate?): VisitStatsResponse {
        val (dateClause, params) = buildDateClause(startDate, endDate)

        val clusters = queryClusters(dateClause, params)
        val hourly = queryHourly(dateClause, params)
        val paths = queryPaths(dateClause, params)

        val totalVisits = clusters.sumOf { it.visitCount }
        val uniqueCountries = clusters.map { it.country }.distinct().size

        return VisitStatsResponse(
            clusters = clusters,
            hourlyDistribution = hourly,
            pathDistribution = paths,
            summary = VisitSummary(uniqueCountries, clusters.size, totalVisits),
            filter = mapOf("startDate" to startDate?.toString(), "endDate" to endDate?.toString()),
        )
    }

    private fun buildDateClause(startDate: LocalDate?, endDate: LocalDate?): Pair<String, Map<String, Any>> {
        val parts = mutableListOf<String>()
        val params = mutableMapOf<String, Any>()
        if (startDate != null) {
            parts += "a.created_at >= :startDate"
            params["startDate"] = startDate
        }
        if (endDate != null) {
            parts += "a.created_at < :endDateNext"
            params["endDateNext"] = endDate.plusDays(1)
        }
        val clause = if (parts.isEmpty()) "" else " AND ${parts.joinToString(" AND ")}"
        return clause to params
    }

    private fun <T> nativeQuery(sql: String, params: Map<String, Any>): List<Array<Any?>> {
        val query = em.createNativeQuery(sql)
        params.forEach { (k, v) -> query.setParameter(k, v) }
        @Suppress("UNCHECKED_CAST")
        return query.resultList as List<Array<Any?>>
    }

    private fun queryClusters(dateClause: String, params: Map<String, Any>): List<VisitCluster> {
        val sql = """
            SELECT g.country, g.region_1, g.region_2, g.latitude, g.longitude,
                   COUNT(DISTINCT (DATE(a.created_at AT TIME ZONE 'Asia/Seoul'), a.ip_geolocation_id)) AS visit_count
            FROM audit_log a
            JOIN ip_geolocations g ON a.ip_geolocation_id = g.id
            WHERE g.latitude IS NOT NULL AND g.longitude IS NOT NULL
              AND g.country IS NOT NULL AND g.region_1 IS NOT NULL AND g.region_1 != ''
              $dateClause
            GROUP BY g.country, g.region_1, g.region_2, g.latitude, g.longitude
            ORDER BY visit_count DESC
        """.trimIndent()

        return nativeQuery<Any>(sql, params).map { row ->
            VisitCluster(
                country = row[0] as String,
                region1 = row[1] as? String ?: "",
                region2 = row[2] as? String ?: "",
                latitude = (row[3] as Number).toDouble(),
                longitude = (row[4] as Number).toDouble(),
                visitCount = (row[5] as Number).toInt(),
            )
        }
    }

    private fun queryHourly(dateClause: String, params: Map<String, Any>): List<HourlyCount> {
        val sql = """
            SELECT EXTRACT(HOUR FROM a.created_at AT TIME ZONE 'Asia/Seoul')::int AS hour,
                   COUNT(DISTINCT (DATE(a.created_at AT TIME ZONE 'Asia/Seoul'), a.ip_geolocation_id)) AS count
            FROM audit_log a
            WHERE a.ip_geolocation_id IS NOT NULL $dateClause
            GROUP BY hour ORDER BY hour
        """.trimIndent()

        val map = nativeQuery<Any>(sql, params).associate { (it[0] as Number).toInt() to (it[1] as Number).toInt() }
        return (0..23).map { HourlyCount(it, map.getOrDefault(it, 0)) }
    }

    private fun queryPaths(dateClause: String, params: Map<String, Any>): List<PathCount> {
        val sql = """
            SELECT a.path, COUNT(DISTINCT (DATE(a.created_at AT TIME ZONE 'Asia/Seoul'), a.ip_geolocation_id)) AS count
            FROM audit_log a
            WHERE a.path IS NOT NULL AND a.server = 'gateway' $dateClause
            GROUP BY a.path ORDER BY count DESC LIMIT 20
        """.trimIndent()

        return nativeQuery<Any>(sql, params).map { PathCount(path = it[0] as String, count = (it[1] as Number).toInt()) }
    }
}
