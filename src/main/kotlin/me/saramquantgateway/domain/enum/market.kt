package me.saramquantgateway.domain.enum

enum class Market {
    KR_KOSPI,
    KR_KOSDAQ,
    US_NYSE,
    US_NASDAQ;

    val isKorean: Boolean
        get() = this == KR_KOSPI || this == KR_KOSDAQ
}