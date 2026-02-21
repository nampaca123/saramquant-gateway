package me.saramquantgateway.domain.enum.stock

enum class PricePeriod(val tradingDays: Int) {
    `1M`(21), `3M`(63), `6M`(126), `1Y`(252)
}
