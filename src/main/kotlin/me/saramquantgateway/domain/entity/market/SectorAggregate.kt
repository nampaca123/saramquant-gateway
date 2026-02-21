package me.saramquantgateway.domain.entity.market

import me.saramquantgateway.domain.enum.stock.Market
import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate

data class SectorAggregateId(
    val market: Market = Market.KR_KOSPI,
    val sector: String = "",
    val date: LocalDate = LocalDate.MIN,
) : Serializable

@Entity
@Table(name = "sector_aggregates")
@IdClass(SectorAggregateId::class)
class SectorAggregate(
    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "market_type")
    val market: Market,

    @Id
    @Column(nullable = false, length = 100)
    val sector: String,

    @Id
    val date: LocalDate,

    @Column(name = "stock_count", nullable = false)
    val stockCount: Int,

    @Column(name = "median_per", precision = 12, scale = 4)
    val medianPer: BigDecimal? = null,

    @Column(name = "median_pbr", precision = 12, scale = 4)
    val medianPbr: BigDecimal? = null,

    @Column(name = "median_roe", precision = 12, scale = 6)
    val medianRoe: BigDecimal? = null,

    @Column(name = "median_operating_margin", precision = 12, scale = 6)
    val medianOperatingMargin: BigDecimal? = null,

    @Column(name = "median_debt_ratio", precision = 12, scale = 6)
    val medianDebtRatio: BigDecimal? = null,
)
