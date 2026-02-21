package me.saramquantgateway.feature.stock.controller

import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.enum.stock.PricePeriod
import me.saramquantgateway.feature.stock.dto.BenchmarkComparisonResponse
import me.saramquantgateway.feature.stock.dto.PriceSeriesResponse
import me.saramquantgateway.feature.stock.dto.StockDetailResponse
import me.saramquantgateway.feature.stock.service.StockService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/stocks")
class StockController(private val service: StockService) {

    @GetMapping("/{symbol}")
    fun detail(
        @PathVariable symbol: String,
        @RequestParam market: Market,
        @RequestParam(defaultValue = "ko") lang: String,
    ): ResponseEntity<StockDetailResponse> =
        ResponseEntity.ok(service.getDetail(symbol, market, lang))

    @GetMapping("/{symbol}/prices")
    fun prices(
        @PathVariable symbol: String,
        @RequestParam market: Market,
        @RequestParam(defaultValue = "1Y") period: PricePeriod,
    ): ResponseEntity<PriceSeriesResponse> =
        ResponseEntity.ok(service.getPrices(symbol, market, period))

    @GetMapping("/{symbol}/benchmark")
    fun benchmark(
        @PathVariable symbol: String,
        @RequestParam market: Market,
        @RequestParam(defaultValue = "1Y") period: PricePeriod,
    ): ResponseEntity<BenchmarkComparisonResponse> =
        ResponseEntity.ok(service.getBenchmark(symbol, market, period))
}
