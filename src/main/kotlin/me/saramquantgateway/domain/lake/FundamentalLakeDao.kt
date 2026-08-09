package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.entity.fundamental.FinancialStatement
import me.saramquantgateway.domain.entity.fundamental.StockFundamental
import me.saramquantgateway.domain.enum.fundamental.ReportType
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class FundamentalLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
) {

    fun findLatestByStockId(stockId: Long): StockFundamental? = executor.query(
        """
        SELECT $FUNDAMENTAL_COLUMNS FROM ${fundamentalRef()}
        WHERE stock_id = ? AND date >= ? ORDER BY date DESC LIMIT 1
        """.trimIndent(),
        listOf(stockId, latestLookbackFrom()),
        ::mapFundamental,
    ).firstOrNull()

    fun findLatestByStockIds(stockIds: List<Long>): List<StockFundamental> {
        if (stockIds.isEmpty()) return emptyList()
        return executor.query(
            """
            SELECT $FUNDAMENTAL_COLUMNS FROM (
                SELECT stock_id, date, per, pbr, eps, bps, roe, debt_ratio, operating_margin,
                       row_number() OVER (PARTITION BY stock_id ORDER BY date DESC) AS rn
                FROM ${fundamentalRef()}
                WHERE date >= ? AND stock_id IN (${placeholders(stockIds.size)})
            ) t WHERE rn = 1
            """.trimIndent(),
            listOf(latestLookbackFrom()) + stockIds,
            ::mapFundamental,
        )
    }

    // report_type은 Postgres enum 순서(Q1<Q2<Q3<FY)를 따르던 정렬이라 CASE로 동일하게 재현한다.
    fun findFinancialsByStockId(stockId: Long, market: Market): List<FinancialStatement> = executor.query(
        """
        SELECT $STATEMENT_COLUMNS FROM ${statementRef()}
        WHERE market = ? AND stock_id = ?
        ORDER BY fiscal_year DESC, CASE report_type
            WHEN 'Q1' THEN 1 WHEN 'Q2' THEN 2 WHEN 'Q3' THEN 3 WHEN 'FY' THEN 4 ELSE 0 END DESC
        """.trimIndent(),
        listOf(marketGroupOf(market), stockId),
        ::mapStatement,
    )

    private fun fundamentalRef() = resolver.ref(FUNDAMENTAL_TABLE)

    private fun statementRef() = resolver.ref(STATEMENT_TABLE)

    private companion object {
        const val FUNDAMENTAL_TABLE = "stock_fundamentals"
        const val STATEMENT_TABLE = "financial_statements"
        const val FUNDAMENTAL_COLUMNS = "stock_id, date, per, pbr, eps, bps, roe, debt_ratio, operating_margin"
        const val STATEMENT_COLUMNS = """
            stock_id, fiscal_year, report_type, revenue, operating_income, net_income,
            total_assets, total_liabilities, total_equity, shares_outstanding
        """

        fun mapFundamental(rs: ResultSet) = StockFundamental(
            stockId = rs.getLong("stock_id"),
            date = rs.localDate("date"),
            per = rs.decimal("per"),
            pbr = rs.decimal("pbr"),
            eps = rs.decimal("eps"),
            bps = rs.decimal("bps"),
            roe = rs.decimal("roe"),
            debtRatio = rs.decimal("debt_ratio"),
            operatingMargin = rs.decimal("operating_margin"),
        )

        fun mapStatement(rs: ResultSet) = FinancialStatement(
            stockId = rs.getLong("stock_id"),
            fiscalYear = rs.getInt("fiscal_year"),
            reportType = ReportType.valueOf(rs.getString("report_type")),
            revenue = rs.decimal("revenue"),
            operatingIncome = rs.decimal("operating_income"),
            netIncome = rs.decimal("net_income"),
            totalAssets = rs.decimal("total_assets"),
            totalLiabilities = rs.decimal("total_liabilities"),
            totalEquity = rs.decimal("total_equity"),
            sharesOutstanding = rs.longOrNull("shares_outstanding"),
        )
    }
}
