package me.saramquantgateway.domain.repository.recommendation

import me.saramquantgateway.domain.entity.recommendation.PortfolioRecommendation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PortfolioRecommendationRepository : JpaRepository<PortfolioRecommendation, Long> {

    fun findByUserIdAndMarketGroupOrderByCreatedAtDesc(
        userId: UUID, marketGroup: String, pageable: Pageable,
    ): Page<PortfolioRecommendation>
}
