package me.saramquantgateway.domain.entity.indicator

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class StockIndicator(
    val stockId: Long,
    val date: LocalDate,
    val sma20: BigDecimal? = null,
    val ema20: BigDecimal? = null,
    val wma20: BigDecimal? = null,
    val rsi14: BigDecimal? = null,
    val macd: BigDecimal? = null,
    val macdSignal: BigDecimal? = null,
    val macdHist: BigDecimal? = null,
    val stochK: BigDecimal? = null,
    val stochD: BigDecimal? = null,
    val bbUpper: BigDecimal? = null,
    val bbMiddle: BigDecimal? = null,
    val bbLower: BigDecimal? = null,
    val atr14: BigDecimal? = null,
    val adx14: BigDecimal? = null,
    val plusDi: BigDecimal? = null,
    val minusDi: BigDecimal? = null,
    val obv: Long? = null,
    val vma20: Long? = null,
    val sar: BigDecimal? = null,
    val beta: BigDecimal? = null,
    val alpha: BigDecimal? = null,
    val sharpe: BigDecimal? = null,
    val createdAt: Instant = Instant.now(),
)
