package me.saramquantgateway.domain.entity.user

import me.saramquantgateway.domain.enum.user.Gender
import me.saramquantgateway.domain.enum.user.InvestmentExperience
import me.saramquantgateway.domain.enum.stock.Market
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_profiles")
class UserProfile(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: UUID,

    @Column(length = 30)
    var nickname: String? = null,

    @Column(name = "birth_year")
    var birthYear: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "gender_type")
    var gender: Gender? = null,

    @Column(name = "profile_image_url")
    var profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_experience", nullable = false, columnDefinition = "investment_experience_type")
    var investmentExperience: InvestmentExperience = InvestmentExperience.BEGINNER,

    @ElementCollection(targetClass = Market::class, fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_preferred_markets",
        joinColumns = [JoinColumn(name = "user_profile_id")]
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "market", nullable = false, columnDefinition = "market_type")
    var preferredMarkets: MutableSet<Market> = mutableSetOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
