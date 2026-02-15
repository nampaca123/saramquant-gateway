package me.saramquantgateway.domain.entity.indicator

import me.saramquantgateway.domain.entity.stock.StockDateId
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "stock_indicators")
@IdClass(StockDateId::class)
class StockIndicator(
    @Id @Column(name = "stock_id")
    val stockId: Long,

    @Id
    val date: LocalDate,

    @Column(name = "sma_20")      val sma20: BigDecimal? = null,
    @Column(name = "ema_20")      val ema20: BigDecimal? = null,
    @Column(name = "wma_20")      val wma20: BigDecimal? = null,
    @Column(name = "rsi_14")      val rsi14: BigDecimal? = null,
    val macd: BigDecimal? = null,
    @Column(name = "macd_signal") val macdSignal: BigDecimal? = null,
    @Column(name = "macd_hist")   val macdHist: BigDecimal? = null,
    @Column(name = "stoch_k")     val stochK: BigDecimal? = null,
    @Column(name = "stoch_d")     val stochD: BigDecimal? = null,
    @Column(name = "bb_upper")    val bbUpper: BigDecimal? = null,
    @Column(name = "bb_middle")   val bbMiddle: BigDecimal? = null,
    @Column(name = "bb_lower")    val bbLower: BigDecimal? = null,
    @Column(name = "atr_14")      val atr14: BigDecimal? = null,
    @Column(name = "adx_14")      val adx14: BigDecimal? = null,
    @Column(name = "plus_di")     val plusDi: BigDecimal? = null,
    @Column(name = "minus_di")    val minusDi: BigDecimal? = null,
    val obv: Long? = null,
    @Column(name = "vma_20")      val vma20: Long? = null,
    val sar: BigDecimal? = null,
    val beta: BigDecimal? = null,
    val alpha: BigDecimal? = null,
    val sharpe: BigDecimal? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)