package me.saramquantgateway.domain.entity

import me.saramquantgateway.domain.enum.Market
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "stocks")
class Stock(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 20)
    val symbol: String,

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "market_type")
    val market: Market,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,

    @Column(name = "dart_corp_code", length = 8)
    val dartCorpCode: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)