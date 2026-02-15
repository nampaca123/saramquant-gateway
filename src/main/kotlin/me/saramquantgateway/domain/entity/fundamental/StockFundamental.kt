package me.saramquantgateway.domain.entity.fundamental

import me.saramquantgateway.domain.entity.stock.StockDateId
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "stock_fundamentals")
@IdClass(StockDateId::class)
class StockFundamental(
    @Id @Column(name = "stock_id")
    val stockId: Long,

    @Id
    val date: LocalDate,

    val per: BigDecimal? = null,
    val pbr: BigDecimal? = null,
    val eps: BigDecimal? = null,
    val bps: BigDecimal? = null,
    val roe: BigDecimal? = null,
    @Column(name = "debt_ratio")       val debtRatio: BigDecimal? = null,
    @Column(name = "operating_margin") val operatingMargin: BigDecimal? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)