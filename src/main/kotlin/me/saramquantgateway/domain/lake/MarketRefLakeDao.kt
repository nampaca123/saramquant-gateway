package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.entity.market.ExchangeRate
import me.saramquantgateway.domain.entity.market.RiskFreeRate
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.time.LocalDate

// maturity 컬럼은 enum 이름이 아니라 라벨('1Y' 등)로 저장된다.
@Component
class MarketRefLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
) {

    fun findExchangeRateOnOrBefore(pair: String, date: LocalDate): ExchangeRate? = executor.query(
        "SELECT pair, date, rate FROM ${resolver.ref(EXCHANGE_TABLE)} WHERE pair = ? AND date <= ? ORDER BY date DESC LIMIT 1",
        listOf(pair, date),
    ) { rs ->
        ExchangeRate(pair = rs.getString("pair"), date = rs.localDate("date"), rate = rs.getBigDecimal("rate"))
    }.firstOrNull()

    fun findLatestRiskFreeRate(country: Country, maturity: Maturity): RiskFreeRate? = executor.query(
        """
        SELECT country, maturity, date, rate FROM ${resolver.ref(RISK_FREE_TABLE)}
        WHERE country = ? AND maturity = ? ORDER BY date DESC LIMIT 1
        """.trimIndent(),
        listOf(country.name, maturity.label),
    ) { rs ->
        RiskFreeRate(
            country = Country.valueOf(rs.getString("country")),
            maturity = Maturity.fromLabel(rs.getString("maturity")),
            date = rs.localDate("date"),
            rate = rs.getBigDecimal("rate"),
        )
    }.firstOrNull()

    private companion object {
        const val EXCHANGE_TABLE = "exchange_rates"
        const val RISK_FREE_TABLE = "risk_free_rates"
    }
}
