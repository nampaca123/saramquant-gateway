package me.saramquantgateway.feature.dashboard.controller

import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.feature.dashboard.dto.DashboardPage
import me.saramquantgateway.feature.dashboard.service.DashboardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/dashboard")
class DashboardController(private val service: DashboardService) {

    @GetMapping("/stocks")
    fun stocks(
        @RequestParam market: Market,
        @RequestParam(required = false) tier: String?,
        @RequestParam(required = false) sector: String?,
        @RequestParam(defaultValue = "name_asc") sort: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<DashboardPage> {
        val tiers = tier?.split(",")?.map { it.trim().uppercase() }
        return ResponseEntity.ok(service.list(market, tiers, sector, sort, page, size))
    }

    @GetMapping("/sectors")
    fun sectors(@RequestParam market: Market): ResponseEntity<List<String>> =
        ResponseEntity.ok(service.sectors(market))
}
