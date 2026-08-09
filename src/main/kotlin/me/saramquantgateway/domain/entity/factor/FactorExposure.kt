package me.saramquantgateway.domain.entity.factor

import java.math.BigDecimal
import java.time.LocalDate

data class FactorExposure(
    val stockId: Long,
    val date: LocalDate,
    val sizeZ: BigDecimal? = null,
    val valueZ: BigDecimal? = null,
    val momentumZ: BigDecimal? = null,
    val volatilityZ: BigDecimal? = null,
    val qualityZ: BigDecimal? = null,
    val leverageZ: BigDecimal? = null,
)
