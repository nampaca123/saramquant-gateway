package me.saramquantgateway.domain.repository.portfolio

import me.saramquantgateway.domain.entity.portfolio.UserPortfolio
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPortfolioRepository : JpaRepository<UserPortfolio, Long> {

    fun findByUserId(userId: UUID): List<UserPortfolio>

    fun findByUserIdAndMarketGroup(userId: UUID, marketGroup: String): UserPortfolio?
}
