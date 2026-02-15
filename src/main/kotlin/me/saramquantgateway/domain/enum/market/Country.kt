package me.saramquantgateway.domain.enum.market

import me.saramquantgateway.domain.enum.stock.Market

enum class Country {
    KR,
    US;

    companion object {
        fun forMarket(market: Market): Country =
            if (market.isKorean) KR else US
    }
}