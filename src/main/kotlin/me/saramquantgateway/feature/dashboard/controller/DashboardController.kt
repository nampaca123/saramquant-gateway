package me.saramquantgateway.feature.dashboard.controller

import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.feature.dashboard.dto.DataFreshnessResponse
import me.saramquantgateway.feature.dashboard.dto.DashboardPage
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import me.saramquantgateway.feature.dashboard.dto.StockSearchResult
import me.saramquantgateway.feature.dashboard.service.DashboardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(private val service: DashboardService) {

    @GetMapping("/stocks")
    fun stocks(
        @RequestParam(required = false) market: Market?,
        @RequestParam(required = false) tier: String?,
        @RequestParam(required = false) sector: String?,
        @RequestParam(defaultValue = "name_asc") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) betaMin: BigDecimal?,
        @RequestParam(required = false) betaMax: BigDecimal?,
        @RequestParam(required = false) rsiMin: BigDecimal?,
        @RequestParam(required = false) rsiMax: BigDecimal?,
        @RequestParam(required = false) sharpeMin: BigDecimal?,
        @RequestParam(required = false) sharpeMax: BigDecimal?,
        @RequestParam(required = false) atrMin: BigDecimal?,
        @RequestParam(required = false) atrMax: BigDecimal?,
        @RequestParam(required = false) adxMin: BigDecimal?,
        @RequestParam(required = false) adxMax: BigDecimal?,
        @RequestParam(required = false) perMin: BigDecimal?,
        @RequestParam(required = false) perMax: BigDecimal?,
        @RequestParam(required = false) pbrMin: BigDecimal?,
        @RequestParam(required = false) pbrMax: BigDecimal?,
        @RequestParam(required = false) roeMin: BigDecimal?,
        @RequestParam(required = false) roeMax: BigDecimal?,
        @RequestParam(required = false) debtRatioMin: BigDecimal?,
        @RequestParam(required = false) debtRatioMax: BigDecimal?,
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) priceHeatTier: String?,
        @RequestParam(required = false) volatilityTier: String?,
        @RequestParam(required = false) trendTier: String?,
        @RequestParam(required = false) companyHealthTier: String?,
        @RequestParam(required = false) valuationTier: String?,
    ): ResponseEntity<DashboardPage> {
        val filter = ScreenerFilter(
            market = market?.name,
            tiers = tier?.split(",")?.map { it.trim().uppercase() },
            sector = sector,
            sort = sort,
            page = page,
            size = size,
            betaMin = betaMin, betaMax = betaMax,
            rsiMin = rsiMin, rsiMax = rsiMax,
            sharpeMin = sharpeMin, sharpeMax = sharpeMax,
            atrMin = atrMin, atrMax = atrMax,
            adxMin = adxMin, adxMax = adxMax,
            perMin = perMin, perMax = perMax,
            pbrMin = pbrMin, pbrMax = pbrMax,
            roeMin = roeMin, roeMax = roeMax,
            debtRatioMin = debtRatioMin, debtRatioMax = debtRatioMax,
            query = query,
            priceHeatTiers = priceHeatTier?.split(",")?.map { it.trim().uppercase() },
            volatilityTiers = volatilityTier?.split(",")?.map { it.trim().uppercase() },
            trendTiers = trendTier?.split(",")?.map { it.trim().uppercase() },
            companyHealthTiers = companyHealthTier?.split(",")?.map { it.trim().uppercase() },
            valuationTiers = valuationTier?.split(",")?.map { it.trim().uppercase() },
        )
        return ResponseEntity.ok(service.list(filter))
    }

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(required = false) market: Market?,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ResponseEntity<List<StockSearchResult>> {
        if (q.isBlank()) return ResponseEntity.ok(emptyList())
        return ResponseEntity.ok(service.search(q.trim(), market, limit.coerceIn(1, 20)))
    }

    @GetMapping("/sectors")
    fun sectors(@RequestParam(required = false) market: Market?): ResponseEntity<List<String>> =
        ResponseEntity.ok(service.sectors(market))

    @GetMapping("/data-freshness")
    fun dataFreshness(): ResponseEntity<DataFreshnessResponse> =
        ResponseEntity.ok(service.dataFreshness())
}
