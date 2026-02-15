package me.saramquantgateway.domain.enum.market

import me.saramquantgateway.domain.enum.stock.Market

enum class Benchmark {
    KR_KOSPI,
    KR_KOSDAQ,
    US_SP500,
    US_NASDAQ;

    companion object {
        fun forMarket(market: Market): Benchmark = when (market) {
            Market.KR_KOSPI  -> KR_KOSPI
            Market.KR_KOSDAQ -> KR_KOSDAQ
            Market.US_NYSE   -> US_SP500
            Market.US_NASDAQ -> US_NASDAQ
        }
    }
}