package me.saramquantgateway.feature.portfolio.service

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.document.PortfolioDoc
import me.saramquantgateway.domain.document.PortfolioEntry
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.repository.stock.DailyPriceRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.domain.store.PortfolioStore
import me.saramquantgateway.feature.portfolio.dto.*
import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Service
class PortfolioService(
    private val portfolioStore: PortfolioStore,
    private val stockRepo: StockRepository,
    private val riskBadgeRepo: RiskBadgeRepository,
    private val priceRepo: DailyPriceRepository,
    private val calcClient: CalcServerClient,
) {

    fun getPortfolios(userId: UUID): List<PortfolioSummary> =
        ensurePortfoliosExist(userId).portfolios.map { it.toSummary() }

    fun getPortfolioDetail(portfolioId: Long, userId: UUID): PortfolioDetail {
        val portfolio = verifyOwnership(portfolioId, userId)
        val holdings = portfolio.holdings.toList()
        if (holdings.isEmpty()) {
            return PortfolioDetail(portfolio.id, portfolio.marketGroup, emptyList(), portfolio.createdAt)
        }

        val stockIds = holdings.map { it.stockId }
        val stockMap = stockRepo.findByIdIn(stockIds).associateBy { it.id }
        val badgeMap = riskBadgeRepo.findByStockIdIn(stockIds).associateBy { it.stockId }
        val priceMap = priceRepo.findTop2PerStockByStockIdIn(stockIds).groupBy { it.stockId }

        var totalCost = BigDecimal.ZERO
        var totalValue = BigDecimal.ZERO

        val holdingDetails = holdings.map { h ->
            val stock = stockMap[h.stockId]
            val badge = badgeMap[h.stockId]
            val prices = priceMap[h.stockId]?.sortedByDescending { it.date }
            val latest = prices?.firstOrNull()?.close
            val prev = prices?.getOrNull(1)?.close
            val changePct = if (latest != null && prev != null && prev.signum() != 0)
                latest.subtract(prev).multiply(BigDecimal(100)).divide(prev, 2, RoundingMode.HALF_UP).toDouble()
            else null

            val cost = h.avgPrice.multiply(h.shares)
            val value = latest?.multiply(h.shares)
            val pnl = if (value != null) value.subtract(cost) else null
            val pnlPct = if (pnl != null && cost.signum() != 0)
                pnl.multiply(BigDecimal(100)).divide(cost, 2, RoundingMode.HALF_UP).toDouble()
            else null

            totalCost = totalCost.add(cost)
            if (value != null) totalValue = totalValue.add(value)

            HoldingDetail(
                id = h.stockId,
                stockId = h.stockId,
                symbol = stock?.symbol ?: "?",
                name = stock?.name ?: "Unknown",
                market = stock?.market?.name,
                sector = stock?.sector,
                shares = h.shares,
                avgPrice = h.avgPrice,
                currency = h.currency,
                purchasedAt = h.purchasedAt.toString(),
                purchaseFxRate = h.purchaseFxRate,
                priceSource = h.priceSource,
                latestClose = latest,
                priceChangePercent = changePct,
                summaryTier = badge?.summaryTier,
                dimensionTiers = badge?.dimensions?.let(::extractDimensionTiers),
                unrealizedPnl = pnl?.setScale(2, RoundingMode.HALF_UP),
                unrealizedPnlPercent = pnlPct,
                currentValue = value?.setScale(2, RoundingMode.HALF_UP),
                costBasis = cost.setScale(2, RoundingMode.HALF_UP),
            )
        }

        val totalPnl = totalValue.subtract(totalCost)
        val totalPnlPct = if (totalCost.signum() != 0)
            totalPnl.multiply(BigDecimal(100)).divide(totalCost, 2, RoundingMode.HALF_UP).toDouble()
        else null

        return PortfolioDetail(
            id = portfolio.id,
            marketGroup = portfolio.marketGroup,
            holdings = holdingDetails,
            createdAt = portfolio.createdAt,
            totalCost = totalCost.setScale(2, RoundingMode.HALF_UP),
            totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
            totalPnl = totalPnl.setScale(2, RoundingMode.HALF_UP),
            totalPnlPercent = totalPnlPct,
        )
    }

    fun buy(portfolioId: Long, userId: UUID, req: BuyRequest): HoldingDetail {
        val (doc, portfolio) = loadOwned(portfolioId, userId)

        val stock = stockRepo.findById(req.stockId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found") }

        val isKr = stock.market.isKorean
        if (isKr && portfolio.marketGroup != "KR")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "KR stock cannot be added to US portfolio")
        if (!isKr && portfolio.marketGroup != "US")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "US stock cannot be added to KR portfolio")

        val resolved = resolvePrice(req)
        val price = resolved.first
        val fxRate = if (!isKr) resolved.second else null
        val currency = if (isKr) "KRW" else "USD"

        val existing = portfolio.holdings.firstOrNull { it.stockId == req.stockId }
        val holding = if (existing != null) {
            val totalShares = existing.shares.add(req.shares)
            val newAvg = existing.shares.multiply(existing.avgPrice)
                .add(req.shares.multiply(price))
                .divide(totalShares, 4, RoundingMode.HALF_UP)

            if (fxRate != null && existing.purchaseFxRate != null) {
                existing.purchaseFxRate = existing.shares.multiply(existing.purchaseFxRate)
                    .add(req.shares.multiply(fxRate))
                    .divide(totalShares, 4, RoundingMode.HALF_UP)
            } else if (fxRate != null) {
                existing.purchaseFxRate = fxRate
            }

            existing.shares = totalShares
            existing.avgPrice = newAvg
            existing.updatedAt = Instant.now()
            existing
        } else {
            HoldingEntry(
                stockId = req.stockId,
                shares = req.shares,
                avgPrice = price,
                currency = currency,
                purchasedAt = req.purchasedAt,
                purchaseFxRate = fxRate,
                priceSource = if (req.manualPrice != null) "MANUAL" else "AUTO",
            ).also { portfolio.holdings.add(it) }
        }

        saveEntry(doc, portfolio)

        return HoldingDetail(
            id = holding.stockId,
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

    fun sell(portfolioId: Long, holdingId: Long, userId: UUID, req: SellRequest) {
        val (doc, portfolio) = loadOwned(portfolioId, userId)
        val holding = findHolding(portfolio, holdingId)
        if (req.sellShares.compareTo(BigDecimal.ZERO) <= 0)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sell_shares must be positive")

        val remaining = holding.shares.subtract(req.sellShares)
        when {
            remaining.compareTo(BigDecimal.ZERO) == 0 -> portfolio.holdings.remove(holding)
            remaining.compareTo(BigDecimal.ZERO) > 0 -> {
                holding.shares = remaining
                holding.updatedAt = Instant.now()
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sell more than owned")
        }
        saveEntry(doc, portfolio)
    }

    fun deleteHolding(portfolioId: Long, holdingId: Long, userId: UUID) {
        val (doc, portfolio) = loadOwned(portfolioId, userId)
        portfolio.holdings.remove(findHolding(portfolio, holdingId))
        saveEntry(doc, portfolio)
    }

    fun reset(portfolioId: Long, userId: UUID) {
        val (doc, portfolio) = loadOwned(portfolioId, userId)
        portfolio.holdings.clear()
        saveEntry(doc, portfolio)
    }

    fun verifyOwnership(portfolioId: Long, userId: UUID): PortfolioEntry = loadOwned(portfolioId, userId).second

    fun ensurePortfoliosExist(userId: UUID): PortfolioDoc {
        val doc = portfolioStore.findByUserId(userId) ?: PortfolioDoc(userId)
        val groups = doc.portfolios.map { it.marketGroup }.toSet()
        val missing = MARKET_GROUPS.filter { it !in groups }
        if (missing.isEmpty()) return doc

        missing.forEach {
            doc.portfolios.add(PortfolioEntry(id = PortfolioStore.portfolioIdOf(userId, it), marketGroup = it))
        }
        doc.portfolios.sortBy { MARKET_GROUPS.indexOf(it.marketGroup) }
        portfolioStore.save(doc)
        return doc
    }

    private fun loadOwned(portfolioId: Long, userId: UUID): Pair<PortfolioDoc, PortfolioEntry> {
        val doc = ensurePortfoliosExist(userId)
        val portfolio = doc.portfolios.firstOrNull { it.id == portfolioId }
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your portfolio")
        return doc to portfolio
    }

    private fun findHolding(portfolio: PortfolioEntry, holdingId: Long): HoldingEntry =
        portfolio.holdings.firstOrNull { it.stockId == holdingId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Holding not found")

    private fun saveEntry(doc: PortfolioDoc, portfolio: PortfolioEntry) {
        portfolio.updatedAt = Instant.now()
        portfolioStore.save(doc)
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

    @Suppress("UNCHECKED_CAST")
    private fun extractDimensionTiers(dims: Map<String, Any>): Map<String, String>? {
        val list = dims["dims"] as? List<Map<String, Any>> ?: return null
        return list.mapNotNull { d ->
            val name = d["name"]?.toString() ?: return@mapNotNull null
            val tier = d["tier"]?.toString() ?: return@mapNotNull null
            name to tier
        }.toMap().ifEmpty { null }
    }

    private fun PortfolioEntry.toSummary() = PortfolioSummary(
        id = id,
        marketGroup = marketGroup,
        holdingsCount = holdings.size,
        createdAt = createdAt,
    )

    companion object {
        private val MARKET_GROUPS = listOf("KR", "US")
    }
}
