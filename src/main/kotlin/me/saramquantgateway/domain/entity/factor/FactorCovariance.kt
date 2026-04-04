package me.saramquantgateway.domain.entity.factor

import jakarta.persistence.*
import me.saramquantgateway.domain.enum.stock.Market
import java.time.LocalDate

@Entity
@Table(name = "factor_covariance")
@IdClass(FactorCovarianceId::class)
class FactorCovariance(
    @Id @Enumerated(EnumType.STRING)
    val market: Market,

    @Id
    val date: LocalDate,

    @Column(columnDefinition = "jsonb", nullable = false)
    val matrix: String,
)

data class FactorCovarianceId(
    val market: Market? = null,
    val date: LocalDate? = null,
) : java.io.Serializable
