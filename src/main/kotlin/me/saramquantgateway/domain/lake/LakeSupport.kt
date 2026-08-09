package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.enum.portfolio.MarketGroup
import me.saramquantgateway.domain.enum.stock.Market
import java.math.BigDecimal
import java.sql.ResultSet
import java.time.LocalDate

// 다종목 스캔만 범위를 좁히되 벽시계가 아니라 테이블의 최신 날짜를 기준으로 삼는다.
internal const val LATEST_LOOKBACK_DAYS = 10L

internal fun recentDatesFrom(ref: String, whereSql: String = ""): String =
    "(SELECT max(date) - INTERVAL '$LATEST_LOOKBACK_DAYS days' FROM $ref$whereSql)"

// daily_prices/financial_statements의 market 파티션 값(KR|US)
internal fun marketGroupOf(market: Market): MarketGroup =
    if (market.isKorean) MarketGroup.KR else MarketGroup.US

internal fun placeholders(count: Int): String = List(count) { "?" }.joinToString(", ")

internal fun ResultSet.longOrNull(column: String): Long? = getLong(column).takeUnless { wasNull() }

internal fun ResultSet.decimal(column: String): BigDecimal? = getBigDecimal(column)

internal fun ResultSet.localDate(column: String): LocalDate = getDate(column).toLocalDate()
