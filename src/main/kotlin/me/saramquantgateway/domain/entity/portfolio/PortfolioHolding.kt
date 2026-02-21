package me.saramquantgateway.domain.entity.portfolio

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "portfolio_holdings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["portfolio_id", "stock_id"])]
)
class PortfolioHolding(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "portfolio_id", nullable = false)
    val portfolioId: Long,

    @Column(name = "stock_id", nullable = false)
    val stockId: Long,

    @Column(nullable = false, precision = 15, scale = 4)
    var shares: BigDecimal,

    @Column(name = "avg_price", nullable = false, precision = 15, scale = 4)
    var avgPrice: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "purchased_at", nullable = false)
    val purchasedAt: LocalDate,

    @Column(name = "purchase_fx_rate", precision = 12, scale = 4)
    var purchaseFxRate: BigDecimal? = null,

    @Column(name = "price_source", nullable = false, length = 20)
    var priceSource: String = "AUTO",

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
