package me.saramquantgateway.infra.log.dto

data class VisitStatsResponse(
    val clusters: List<VisitCluster>,
    val hourlyDistribution: List<HourlyCount>,
    val pathDistribution: List<PathCount>,
    val summary: VisitSummary,
    val filter: Map<String, String?>,
)

data class VisitCluster(
    val country: String,
    val region1: String,
    val region2: String,
    val latitude: Double,
    val longitude: Double,
    val visitCount: Int,
)

data class HourlyCount(val hour: Int, val count: Int)

data class PathCount(val path: String, val count: Int)

data class VisitSummary(
    val totalCountries: Int,
    val totalRegions: Int,
    val totalVisits: Int,
)
