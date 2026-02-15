package me.saramquantgateway.domain.entity.market

import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "risk_free_rates")
class RiskFreeRate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "country_type")
    val country: Country,

    @Column(nullable = false, columnDefinition = "maturity_type")
    val maturity: Maturity,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, precision = 6, scale = 4)
    val rate: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)