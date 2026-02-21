package me.saramquantgateway.feature.riskbadge.controller

import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.feature.riskbadge.dto.RiskBadgePage
import me.saramquantgateway.feature.riskbadge.service.RiskBadgeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/risk-badges")
class RiskBadgeController(private val service: RiskBadgeService) {

    @GetMapping
    fun list(
        @RequestParam market: Market,
        @RequestParam(required = false) tier: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<RiskBadgePage> =
        ResponseEntity.ok(service.list(market, tier, page, size))
}
