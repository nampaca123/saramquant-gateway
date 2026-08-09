package me.saramquantgateway.domain.document

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// 사용자 한 명의 KR/US 포트폴리오와 보유종목을 한 문서에 담는다.
data class PortfolioDoc(
    val userId: UUID,
    val portfolios: MutableList<PortfolioEntry> = mutableListOf(),
)

data class PortfolioEntry(
    val id: Long,
    val marketGroup: String,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    val holdings: MutableList<HoldingEntry> = mutableListOf(),
)

data class HoldingEntry(
    val stockId: Long,
    var shares: BigDecimal,
    var avgPrice: BigDecimal,
    val currency: String,
    val purchasedAt: LocalDate,
    var purchaseFxRate: BigDecimal? = null,
    var priceSource: String = "AUTO",
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)
