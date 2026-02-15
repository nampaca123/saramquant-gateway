package me.saramquantgateway.domain.enum

enum class Country {
    KR,
    US;

    companion object {
        fun forMarket(market: Market): Country =
            if (market.isKorean) KR else US
    }
}