package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.entity.market.SectorAggregate
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.LocalDate

@Component
class SectorLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
) {

    fun findLatestByMarketAndSector(market: Market, sector: String): SectorAggregate? = executor.query(
        "SELECT $COLUMNS FROM ${ref()} WHERE market = ? AND sector = ? ORDER BY date DESC LIMIT 1",
        listOf(market.name, sector),
        ::mapAggregate,
    ).firstOrNull()

    fun findLatestByMarket(market: Market): SectorAggregate? = executor.query(
        "SELECT $COLUMNS FROM ${ref()} WHERE market = ? ORDER BY date DESC LIMIT 1",
        listOf(market.name),
        ::mapAggregate,
    ).firstOrNull()

    fun findByMarketAndDate(market: Market, date: LocalDate): List<SectorAggregate> = executor.query(
        "SELECT $COLUMNS FROM ${ref()} WHERE market = ? AND date = ? ORDER BY sector",
        listOf(market.name, date),
        ::mapAggregate,
    )

    private fun ref() = resolver.ref(TABLE)

    private companion object {
        const val TABLE = "sector_aggregates"
        const val COLUMNS = """
            market, sector, date, stock_count, median_per, median_pbr,
            median_roe, median_operating_margin, median_debt_ratio
        """

        fun mapAggregate(rs: ResultSet) = SectorAggregate(
            market = Market.valueOf(rs.getString("market")),
            sector = rs.getString("sector"),
            date = rs.localDate("date"),
            stockCount = rs.getInt("stock_count"),
            medianPer = rs.decimal("median_per"),
            medianPbr = rs.decimal("median_pbr"),
            medianRoe = rs.decimal("median_roe"),
            medianOperatingMargin = rs.decimal("median_operating_margin"),
            medianDebtRatio = rs.decimal("median_debt_ratio"),
        )
    }
}
