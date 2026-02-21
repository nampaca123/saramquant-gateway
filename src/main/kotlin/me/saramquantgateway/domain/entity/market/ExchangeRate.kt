package me.saramquantgateway.domain.entity.market

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "exchange_rates",
    uniqueConstraints = [UniqueConstraint(columnNames = ["pair", "date"])]
)
class ExchangeRate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 7)
    val pair: String,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, precision = 12, scale = 4)
    val rate: BigDecimal,
)
