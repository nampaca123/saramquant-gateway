package me.saramquantgateway.support

import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.duckdb.DuckDBConnection
import java.sql.DriverManager

// 레이크 스키마와 동일한 인메모리 DuckDB 테이블에 DAO의 실제 SQL을 실행하기 위한 시임.
class LakeFixture : AutoCloseable {

    private val connection = DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection

    val executor = DuckDbQueryExecutor(connection)

    // 테스트에서는 iceberg_scan 대신 로컬 테이블명을 그대로 쓴다.
    val resolver = LakeTableResolver { it }

    init {
        execute(*DDL)
    }

    fun execute(vararg statements: String) {
        connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
    }

    override fun close() {
        executor.close()
    }

    private companion object {
        val DDL = arrayOf(
            """CREATE TABLE stocks (
                id BIGINT, symbol VARCHAR, name VARCHAR, market VARCHAR, is_active BOOLEAN,
                dart_corp_code VARCHAR, sector VARCHAR, created_at TIMESTAMP, updated_at TIMESTAMP)""",
            """CREATE TABLE daily_prices (
                market VARCHAR, stock_id BIGINT, date DATE, open DECIMAL(15,2), high DECIMAL(15,2),
                low DECIMAL(15,2), close DECIMAL(15,2), volume BIGINT, created_at TIMESTAMP)""",
            """CREATE TABLE benchmark_daily_prices (
                benchmark VARCHAR, date DATE, close DECIMAL(15,2), created_at TIMESTAMP)""",
            """CREATE TABLE risk_free_rates (
                country VARCHAR, maturity VARCHAR, date DATE, rate DECIMAL(6,4), created_at TIMESTAMP)""",
            "CREATE TABLE exchange_rates (pair VARCHAR, date DATE, rate DECIMAL(12,4))",
            """CREATE TABLE financial_statements (
                market VARCHAR, stock_id BIGINT, fiscal_year INTEGER, report_type VARCHAR,
                revenue DECIMAL(20,2), operating_income DECIMAL(20,2), net_income DECIMAL(20,2),
                total_assets DECIMAL(20,2), total_liabilities DECIMAL(20,2), total_equity DECIMAL(20,2),
                shares_outstanding BIGINT, created_at TIMESTAMP)""",
            """CREATE TABLE stock_fundamentals (
                stock_id BIGINT, date DATE, per DECIMAL(12,4), pbr DECIMAL(12,4), eps DECIMAL(15,4),
                bps DECIMAL(15,4), roe DECIMAL(10,4), debt_ratio DECIMAL(10,4),
                operating_margin DECIMAL(10,4), data_coverage VARCHAR, created_at TIMESTAMP)""",
            """CREATE TABLE stock_indicators (
                stock_id BIGINT, date DATE, sma_20 DECIMAL(15,4), ema_20 DECIMAL(15,4), wma_20 DECIMAL(15,4),
                rsi_14 DECIMAL(8,4), macd DECIMAL(15,4), macd_signal DECIMAL(15,4), macd_hist DECIMAL(15,4),
                stoch_k DECIMAL(8,4), stoch_d DECIMAL(8,4), bb_upper DECIMAL(15,4), bb_middle DECIMAL(15,4),
                bb_lower DECIMAL(15,4), atr_14 DECIMAL(15,4), adx_14 DECIMAL(8,4), plus_di DECIMAL(8,4),
                minus_di DECIMAL(8,4), obv BIGINT, vma_20 BIGINT, sar DECIMAL(15,4), beta DECIMAL(8,4),
                alpha DECIMAL(8,4), sharpe DECIMAL(8,4), created_at TIMESTAMP)""",
            """CREATE TABLE factor_exposures (
                stock_id BIGINT, date DATE, size_z DECIMAL(8,4), value_z DECIMAL(8,4),
                momentum_z DECIMAL(8,4), volatility_z DECIMAL(8,4), quality_z DECIMAL(8,4),
                leverage_z DECIMAL(8,4))""",
            "CREATE TABLE factor_covariance (market VARCHAR, date DATE, matrix VARCHAR)",
            """CREATE TABLE sector_aggregates (
                market VARCHAR, sector VARCHAR, date DATE, stock_count INTEGER, median_per DECIMAL(12,4),
                median_pbr DECIMAL(12,4), median_roe DECIMAL(12,6), median_operating_margin DECIMAL(12,6),
                median_debt_ratio DECIMAL(12,6))""",
            """CREATE TABLE risk_badges (
                stock_id BIGINT, market VARCHAR, date DATE, summary_tier VARCHAR,
                dimensions VARCHAR, updated_at TIMESTAMP)""",
        )
    }
}
