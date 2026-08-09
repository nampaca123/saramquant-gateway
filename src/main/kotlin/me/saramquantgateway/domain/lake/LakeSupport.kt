package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.enum.stock.Market
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate

// 스냅샷이 아닌 파티션 테이블에서 "최신 1건"을 찾을 때 스캔 범위를 이 기간으로 좁힌다.
internal const val LATEST_LOOKBACK_DAYS = 90L

internal fun latestLookbackFrom(): LocalDate = LocalDate.now().minusDays(LATEST_LOOKBACK_DAYS)

// daily_prices/financial_statements의 market 파티션 값(KR|US)
internal fun marketGroupOf(market: Market): String = if (market.isKorean) "KR" else "US"

internal fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

internal fun ResultSet.longOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }

internal fun ResultSet.decimal(column: String): BigDecimal? = getBigDecimal(column)

internal fun ResultSet.localDate(column: String): LocalDate = getDate(column).toLocalDate()
