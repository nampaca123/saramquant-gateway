package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.entity.factor.FactorCovariance
import me.saramquantgateway.domain.entity.factor.FactorExposure
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class FactorLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
) {

    fun findLatestByStockId(stockId: Long): FactorExposure? = executor.query(
        """
        SELECT $EXPOSURE_COLUMNS FROM ${exposureRef()}
        WHERE stock_id = ? ORDER BY date DESC LIMIT 1
        """.trimIndent(),
        listOf(stockId),
        ::mapExposure,
    ).firstOrNull()

    fun findLatestByStockIds(stockIds: List<Long>): List<FactorExposure> {
        if (stockIds.isEmpty()) return emptyList()
        val ref = exposureRef()
        return executor.query(
            """
            SELECT $EXPOSURE_COLUMNS FROM (
                SELECT stock_id, date, size_z, value_z, momentum_z, volatility_z, quality_z, leverage_z,
                       row_number() OVER (PARTITION BY stock_id ORDER BY date DESC) AS rn
                FROM $ref
                WHERE date >= ${recentDatesFrom(ref)} AND stock_id IN (${placeholders(stockIds.size)})
            ) t WHERE rn = 1
            """.trimIndent(),
            stockIds,
            ::mapExposure,
        )
    }

    fun findLatestCovariance(market: Market): FactorCovariance? = executor.query(
        "SELECT market, date, matrix FROM ${covarianceRef()} WHERE market = ? ORDER BY date DESC LIMIT 1",
        listOf(market.name),
    ) { rs ->
        FactorCovariance(
            market = Market.valueOf(rs.getString("market")),
            date = rs.localDate("date"),
            matrix = rs.getString("matrix"),
        )
    }.firstOrNull()

    private fun exposureRef() = resolver.ref(EXPOSURE_TABLE)

    private fun covarianceRef() = resolver.ref(COVARIANCE_TABLE)

    private companion object {
        const val EXPOSURE_TABLE = "factor_exposures"
        const val COVARIANCE_TABLE = "factor_covariance"
        const val EXPOSURE_COLUMNS = "stock_id, date, size_z, value_z, momentum_z, volatility_z, quality_z, leverage_z"

        fun mapExposure(rs: ResultSet) = FactorExposure(
            stockId = rs.getLong("stock_id"),
            date = rs.localDate("date"),
            sizeZ = rs.decimal("size_z"),
            valueZ = rs.decimal("value_z"),
            momentumZ = rs.decimal("momentum_z"),
            volatilityZ = rs.decimal("volatility_z"),
            qualityZ = rs.decimal("quality_z"),
            leverageZ = rs.decimal("leverage_z"),
        )
    }
}
