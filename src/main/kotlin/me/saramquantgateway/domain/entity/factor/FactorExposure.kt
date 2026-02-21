package me.saramquantgateway.domain.entity.factor

import me.saramquantgateway.domain.entity.stock.StockDateId
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(name = "factor_exposures")
@IdClass(StockDateId::class)
class FactorExposure(
    @Id @Column(name = "stock_id")
    val stockId: Long,

    @Id
    val date: LocalDate,

    @Column(name = "size_z", precision = 8, scale = 4)
    val sizeZ: BigDecimal? = null,

    @Column(name = "value_z", precision = 8, scale = 4)
    val valueZ: BigDecimal? = null,

    @Column(name = "momentum_z", precision = 8, scale = 4)
    val momentumZ: BigDecimal? = null,

    @Column(name = "volatility_z", precision = 8, scale = 4)
    val volatilityZ: BigDecimal? = null,

    @Column(name = "quality_z", precision = 8, scale = 4)
    val qualityZ: BigDecimal? = null,

    @Column(name = "leverage_z", precision = 8, scale = 4)
    val leverageZ: BigDecimal? = null,
)
