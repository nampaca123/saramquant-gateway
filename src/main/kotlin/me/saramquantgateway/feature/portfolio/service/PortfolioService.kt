package me.saramquantgateway.feature.portfolio.service

import me.saramquantgateway.domain.entity.portfolio.PortfolioHolding
import me.saramquantgateway.domain.entity.portfolio.UserPortfolio
import me.saramquantgateway.domain.repository.llm.PortfolioLlmAnalysisRepository
import me.saramquantgateway.domain.repository.portfolio.PortfolioHoldingRepository
import me.saramquantgateway.domain.repository.portfolio.UserPortfolioRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.portfolio.dto.*
import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Service
class PortfolioService(
    private val portfolioRepo: UserPortfolioRepository,
    private val holdingRepo: PortfolioHoldingRepository,
    private val stockRepo: StockRepository,
    private val calcClient: CalcServerClient,
    private val llmCacheRepo: PortfolioLlmAnalysisRepository,
) {

    fun getPortfolios(userId: UUID): List<PortfolioSummary> {
        ensurePortfoliosExist(userId)
        return portfolioRepo.findByUserId(userId).map { it.toSummary() }
    }

    fun getPortfolioDetail(portfolioId: Long, userId: UUID): PortfolioDetail {
        val portfolio = verifyOwnership(portfolioId, userId)
        val holdings = holdingRepo.findByPortfolioId(portfolioId)
        val stockMap = if (holdings.isNotEmpty())
            stockRepo.findByIdIn(holdings.map { it.stockId }).associateBy { it.id }
        else emptyMap()

        return PortfolioDetail(
            id = portfolio.id,
            marketGroup = portfolio.marketGroup,
            holdings = holdings.map { h ->
                val stock = stockMap[h.stockId]
                HoldingDetail(
                    id = h.id,
                    stockId = h.stockId,
                    symbol = stock?.symbol ?: "?",
                    name = stock?.name ?: "Unknown",
                    shares = h.shares,
                    avgPrice = h.avgPrice,
                    currency = h.currency,
                    purchasedAt = h.purchasedAt.toString(),
                    purchaseFxRate = h.purchaseFxRate,
                    priceSource = h.priceSource,
                )
            },
            createdAt = portfolio.createdAt,
        )
    }

    @Transactional
    fun buy(portfolioId: Long, userId: UUID, req: BuyRequest): HoldingDetail {
        val portfolio = verifyOwnership(portfolioId, userId)

        val stock = stockRepo.findById(req.stockId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }

        val isKr = stock.market.isKorean
        if (isKr && portfolio.marketGroup != "KR")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "KR stock cannot be added to US portfolio")
        if (!isKr && portfolio.marketGroup != "US")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "US stock cannot be added to KR portfolio")

        if (isKr && req.shares.stripTrailingZeros().scale() > 0)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "KR shares must be integer")

        val resolved = resolvePrice(req)
        val price = resolved.first
        val fxRate = if (!isKr) resolved.second else null
        val currency = if (isKr) "KRW" else "USD"

        val existing = holdingRepo.findByPortfolioIdAndStockId(portfolioId, req.stockId)
        val holding = if (existing != null) {
            val totalShares = existing.shares.add(req.shares)
            val newAvg = existing.shares.multiply(existing.avgPrice)
                .add(req.shares.multiply(price))
                .divide(totalShares, 4, RoundingMode.HALF_UP)

            if (fxRate != null && existing.purchaseFxRate != null) {
                val newFx = existing.shares.multiply(existing.purchaseFxRate)
                    .add(req.shares.multiply(fxRate))
                    .divide(totalShares, 4, RoundingMode.HALF_UP)
                existing.purchaseFxRate = newFx
            } else if (fxRate != null) {
                existing.purchaseFxRate = fxRate
            }

            existing.shares = totalShares
            existing.avgPrice = newAvg
            existing.updatedAt = Instant.now()
            holdingRepo.save(existing)
        } else {
            holdingRepo.save(
                PortfolioHolding(
                    portfolioId = portfolioId,
                    stockId = req.stockId,
                    shares = req.shares,
                    avgPrice = price,
                    currency = currency,
                    purchasedAt = req.purchasedAt,
                    purchaseFxRate = fxRate,
                    priceSource = if (req.manualPrice != null) "MANUAL" else "AUTO",
                )
            )
        }

        llmCacheRepo.deleteByPortfolioId(portfolioId)

        return HoldingDetail(
            id = holding.id,
            stockId = holding.stockId,
            symbol = stock.symbol,
            name = stock.name,
            shares = holding.shares,
            avgPrice = holding.avgPrice,
            currency = holding.currency,
            purchasedAt = holding.purchasedAt.toString(),
            purchaseFxRate = holding.purchaseFxRate,
            priceSource = holding.priceSource,
        )
    }

    @Transactional
    fun sell(portfolioId: Long, holdingId: Long, userId: UUID, req: SellRequest) {
        verifyOwnership(portfolioId, userId)
        val holding = holdingRepo.findById(holdingId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found") }
        if (holding.portfolioId != portfolioId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Holding does not belong to this portfolio")
        if (req.sellShares.compareTo(BigDecimal.ZERO) <= 0)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sell_shares must be positive")

        val remaining = holding.shares.subtract(req.sellShares)
        when {
            remaining.compareTo(BigDecimal.ZERO) == 0 -> holdingRepo.delete(holding)
            remaining.compareTo(BigDecimal.ZERO) > 0 -> {
                holding.shares = remaining
                holding.updatedAt = Instant.now()
                holdingRepo.save(holding)
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sell more than owned")
        }
        llmCacheRepo.deleteByPortfolioId(portfolioId)
    }

    @Transactional
    fun deleteHolding(portfolioId: Long, holdingId: Long, userId: UUID) {
        verifyOwnership(portfolioId, userId)
        val holding = holdingRepo.findById(holdingId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found") }
        if (holding.portfolioId != portfolioId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Holding does not belong to this portfolio")
        holdingRepo.delete(holding)
        llmCacheRepo.deleteByPortfolioId(portfolioId)
    }

    @Transactional
    fun reset(portfolioId: Long, userId: UUID) {
        verifyOwnership(portfolioId, userId)
        holdingRepo.deleteByPortfolioId(portfolioId)
        llmCacheRepo.deleteByPortfolioId(portfolioId)
    }

    fun verifyOwnership(portfolioId: Long, userId: UUID): UserPortfolio {
        val portfolio = portfolioRepo.findById(portfolioId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found") }
        if (portfolio.userId != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your portfolio")
        return portfolio
    }

    @Transactional
    fun ensurePortfoliosExist(userId: UUID) {
        val existing = portfolioRepo.findByUserId(userId)
        val groups = existing.map { it.marketGroup }.toSet()
        for (mg in listOf("KR", "US")) {
            if (mg !in groups) {
                try {
                    portfolioRepo.saveAndFlush(UserPortfolio(userId = userId, marketGroup = mg))
                } catch (_: DataIntegrityViolationException) {
                    // concurrent creation — UNIQUE constraint handles it
                }
            }
        }
    }

    private fun resolvePrice(req: BuyRequest): Pair<BigDecimal, BigDecimal?> {
        if (req.manualPrice != null) return Pair(req.manualPrice, null)

        val body = mapOf("stock_id" to req.stockId, "date" to req.purchasedAt.toString())
        val result = calcClient.post("/internal/portfolios/price-lookup", body)
        if (result != null && result["found"] == true) {
            val close = result["close"]
            if (close is Number) {
                val price = BigDecimal.valueOf(close.toDouble())
                val fxRate = (result["fx_rate"] as? Number)
                    ?.let { BigDecimal.valueOf(it.toDouble()) }
                return Pair(price, fxRate)
            }
        }
        throw ResponseStatusException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Could not resolve price. Please provide manual_price."
        )
    }

    private fun UserPortfolio.toSummary() = PortfolioSummary(
        id = id,
        marketGroup = marketGroup,
        holdingsCount = holdingRepo.findByPortfolioId(id).size,
        createdAt = createdAt,
    )
}
