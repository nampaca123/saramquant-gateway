package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.feature.stock.dto.FactorExposureSnapshot
import me.saramquantgateway.feature.stock.dto.FundamentalSnapshot
import me.saramquantgateway.feature.stock.dto.IndicatorSnapshot
import me.saramquantgateway.feature.stock.dto.SectorComparisonSnapshot
import org.springframework.stereotype.Component
import java.math.BigDecimal

data class StockContextData(
    val name: String, val symbol: String, val market: String, val sector: String?,
    val close: BigDecimal?, val priceChange: Double?, val dataDate: String?,
    val badge: Map<String, Any>?, val summaryTier: String?,
    val indicator: IndicatorSnapshot?,
    val fundamental: FundamentalSnapshot?,
    val sectorComparison: SectorComparisonSnapshot?,
    val factorExposure: FactorExposureSnapshot?,
    val riskFreeRate: BigDecimal?,
)

data class PortfolioContextData(
    val holdings: List<HoldingContext>,
    val riskScore: Map<String, Any?>?,
    val riskDecomp: Map<String, Any?>?,
    val diversification: Map<String, Any?>?,
    val riskFreeRate: BigDecimal?,
    val benchmark: String?,
)

data class HoldingContext(
    val name: String, val symbol: String, val sector: String?,
    val weightPct: Double, val summaryTier: String?,
    val beta: BigDecimal?, val sharpe: BigDecimal?,
    val debtRatio: BigDecimal?, val roe: BigDecimal?, val operatingMargin: BigDecimal?,
    val sectorDebtRatio: BigDecimal?, val sectorRoe: BigDecimal?, val sectorOpMargin: BigDecimal?,
)

@Component
class PromptBuilder {

    fun buildStockPrompt(data: StockContextData, preset: String, lang: String): Pair<String, String> {
        val system = if (lang == "en") SYSTEM_EN else SYSTEM_KO
        val context = buildStockContext(data, lang)
        val presets = if (lang == "en") STOCK_PRESETS_EN else STOCK_PRESETS_KO
        val userPrompt = presets[preset] ?: presets.values.first()
        return Pair(system, "$context\n\n$userPrompt")
    }

    fun buildPortfolioPrompt(data: PortfolioContextData, preset: String, lang: String): Pair<String, String> {
        val system = if (lang == "en") SYSTEM_EN else SYSTEM_KO
        val context = buildPortfolioContext(data, preset, lang)
        val presets = if (lang == "en") PORTFOLIO_PRESETS_EN else PORTFOLIO_PRESETS_KO
        val userPrompt = presets[preset] ?: presets.values.first()
        return Pair(system, "$context\n\n$userPrompt")
    }

    private fun buildStockContext(d: StockContextData, lang: String): String {
        val sb = StringBuilder()
        if (lang == "en") {
            sb.appendLine("=== Stock Data ===")
            sb.appendLine("Name: ${d.name} (${d.symbol}), Market: ${d.market}, Sector: ${d.sector ?: "N/A"}")
            sb.appendLine("Latest Close: ${d.close?.toPlainString() ?: "N/A"}, Change: ${fmt(d.priceChange)}%, Date: ${d.dataDate ?: "N/A"}")
            sb.appendLine("Risk Badge: ${d.summaryTier ?: "N/A"}")
            d.badge?.let { sb.appendLine("Badge Dimensions: $it") }
        } else {
            sb.appendLine("=== 종목 데이터 ===")
            sb.appendLine("종목명: ${d.name} (${d.symbol}), 시장: ${d.market}, 섹터: ${d.sector ?: "N/A"}")
            sb.appendLine("최근 종가: ${d.close?.toPlainString() ?: "N/A"}, 등락률: ${fmt(d.priceChange)}%, 기준일: ${d.dataDate ?: "N/A"}")
            sb.appendLine("리스크 등급: ${d.summaryTier ?: "N/A"}")
            d.badge?.let { sb.appendLine("등급 세부: $it") }
        }

        d.indicator?.let { i ->
            sb.appendLine()
            sb.appendLine(if (lang == "en") "--- Technical Indicators (${i.date}) ---" else "--- 기술적 지표 (${i.date}) ---")
            sb.appendLine("RSI(14): ${dec(i.rsi14)}, MACD: ${dec(i.macd)}, Signal: ${dec(i.macdSignal)}, Hist: ${dec(i.macdHist)}")
            sb.appendLine("Stoch K/D: ${dec(i.stochK)}/${dec(i.stochD)}, ADX: ${dec(i.adx14)}, +DI: ${dec(i.plusDi)}, -DI: ${dec(i.minusDi)}")
            sb.appendLine("BB: ${dec(i.bbUpper)}/${dec(i.bbMiddle)}/${dec(i.bbLower)}, ATR: ${dec(i.atr14)}")
            sb.appendLine("SMA20: ${dec(i.sma20)}, EMA20: ${dec(i.ema20)}, SAR: ${dec(i.sar)}")
            sb.appendLine("Beta: ${dec(i.beta)}, Alpha: ${dec(i.alpha)}, Sharpe: ${dec(i.sharpe)}")
        }

        d.fundamental?.let { f ->
            sb.appendLine()
            sb.appendLine(if (lang == "en") "--- Fundamentals (${f.date}) ---" else "--- 펀더멘털 (${f.date}) ---")
            sb.appendLine("PER: ${dec(f.per)}, PBR: ${dec(f.pbr)}, EPS: ${dec(f.eps)}, BPS: ${dec(f.bps)}")
            sb.appendLine("ROE: ${dec(f.roe)}, Debt Ratio: ${dec(f.debtRatio)}, Op. Margin: ${dec(f.operatingMargin)}")
        }

        d.sectorComparison?.let { s ->
            sb.appendLine()
            sb.appendLine(if (lang == "en") "--- Sector Comparison (${s.sector}, ${s.stockCount} stocks) ---" else "--- 섹터 비교 (${s.sector}, ${s.stockCount}개 종목) ---")
            sb.appendLine("Median PER: ${dec(s.medianPer)}, PBR: ${dec(s.medianPbr)}, ROE: ${dec(s.medianRoe)}")
            sb.appendLine("Median Op.Margin: ${dec(s.medianOperatingMargin)}, Debt Ratio: ${dec(s.medianDebtRatio)}")
        }

        d.factorExposure?.let { f ->
            sb.appendLine()
            sb.appendLine(if (lang == "en") "--- Factor Exposure (${f.date}) ---" else "--- 팩터 노출 (${f.date}) ---")
            sb.appendLine("Size: ${dec(f.sizeZ)}, Value: ${dec(f.valueZ)}, Momentum: ${dec(f.momentumZ)}")
            sb.appendLine("Volatility: ${dec(f.volatilityZ)}, Quality: ${dec(f.qualityZ)}, Leverage: ${dec(f.leverageZ)}")
        }

        d.riskFreeRate?.let {
            sb.appendLine()
            sb.appendLine(if (lang == "en") "Risk-Free Rate: ${it.toPlainString()}%" else "무위험 이자율: ${it.toPlainString()}%")
        }

        return sb.toString().trimEnd()
    }

    private fun buildPortfolioContext(d: PortfolioContextData, preset: String, lang: String): String {
        val sb = StringBuilder()
        val en = lang == "en"

        sb.appendLine(if (en) "=== Portfolio Holdings ===" else "=== 포트폴리오 보유 종목 ===")
        sb.appendLine(if (en) "| Name | Symbol | Sector | Weight% | Tier | Beta | Sharpe |" else "| 종목명 | 심볼 | 섹터 | 비중% | 등급 | Beta | Sharpe |")
        sb.appendLine("|------|--------|--------|---------|------|------|--------|")
        d.holdings.forEach { h ->
            sb.appendLine("| ${h.name} | ${h.symbol} | ${h.sector ?: "N/A"} | ${String.format("%.1f", h.weightPct)} | ${h.summaryTier ?: "N/A"} | ${dec(h.beta)} | ${dec(h.sharpe)} |")
        }

        d.riskScore?.let {
            sb.appendLine()
            sb.appendLine(if (en) "--- Risk Score ---" else "--- 리스크 점수 ---")
            it.forEach { (k, v) -> sb.appendLine("$k: ${v ?: "N/A"}") }
        }

        d.riskDecomp?.let {
            sb.appendLine()
            sb.appendLine(if (en) "--- Risk Decomposition ---" else "--- 리스크 분해 ---")
            it.forEach { (k, v) -> sb.appendLine("$k: ${v ?: "N/A"}") }
        }

        d.diversification?.let {
            sb.appendLine()
            sb.appendLine(if (en) "--- Diversification ---" else "--- 분산도 ---")
            it.forEach { (k, v) -> sb.appendLine("$k: ${v ?: "N/A"}") }
        }

        if (preset in setOf("financial_weakness", "aggressive")) {
            sb.appendLine()
            sb.appendLine(if (en) "--- Per-Holding Fundamentals ---" else "--- 종목별 재무 ---")
            sb.appendLine(if (en) "| Name | DebtRatio | ROE | Op.Margin | Sec.DebtRatio | Sec.ROE | Sec.OpMargin |" else "| 종목명 | 부채비율 | ROE | 영업이익률 | 섹터부채비율 | 섹터ROE | 섹터영업이익률 |")
            sb.appendLine("|------|-----------|-----|-----------|---------------|---------|--------------|")
            d.holdings.forEach { h ->
                sb.appendLine("| ${h.name} | ${dec(h.debtRatio)} | ${dec(h.roe)} | ${dec(h.operatingMargin)} | ${dec(h.sectorDebtRatio)} | ${dec(h.sectorRoe)} | ${dec(h.sectorOpMargin)} |")
            }
        }

        d.riskFreeRate?.let {
            sb.appendLine()
            sb.appendLine(if (en) "Risk-Free Rate: ${it.toPlainString()}%" else "무위험 이자율: ${it.toPlainString()}%")
        }
        d.benchmark?.let {
            sb.appendLine(if (en) "Benchmark: $it" else "벤치마크: $it")
        }

        return sb.toString().trimEnd()
    }

    private fun dec(v: BigDecimal?): String = v?.toPlainString() ?: "N/A"
    private fun fmt(v: Double?): String = if (v != null) String.format("%.2f", v) else "N/A"

    companion object {
        private const val SYSTEM_KO = """당신은 사람퀀트(SaramQuant)의 전문 금융 분석 AI입니다.
주어진 데이터를 기반으로 정확하고 객관적인 분석을 제공하세요.
규칙:
- 주어진 데이터만을 근거로 분석하세요. 추측이나 외부 정보를 사용하지 마세요.
- 수치를 인용할 때는 정확한 값을 사용하세요.
- 분석은 구조화된 마크다운 형식으로 작성하세요.
- 긍정적/부정적 요인을 균형 있게 서술하세요.
- 투자 추천이 아닌 데이터 기반 분석임을 명시하세요.
- 응답은 한국어로 작성하세요."""

        private const val SYSTEM_EN = """You are SaramQuant's professional financial analysis AI.
Provide accurate and objective analysis based on the given data.
Rules:
- Base your analysis solely on the provided data. Do not speculate or use external information.
- Use exact values when citing numbers.
- Write analysis in structured markdown format.
- Present positive and negative factors in a balanced manner.
- Clarify that this is data-driven analysis, not investment advice.
- Respond in English."""

        private val STOCK_PRESETS_KO = mapOf(
            "summary" to "위 데이터를 종합하여 이 종목의 현재 상태를 요약 분석해 주세요. 기술적 지표, 펀더멘털, 리스크 등급, 팩터 노출을 모두 고려하여 핵심 포인트를 정리해 주세요.",
            "beginner" to "투자 초보자도 이해할 수 있도록 위 종목의 현재 상태를 쉽게 설명해 주세요. 전문 용어는 괄호 안에 간단한 설명을 덧붙여 주세요.",
            "risk_assessment" to "위 데이터를 기반으로 이 종목의 리스크를 집중 분석해 주세요. 변동성, 기술적 과매수/과매도, 재무 건전성, 팩터 리스크를 종합적으로 평가해 주세요.",
            "financial_health" to "위 재무 데이터와 섹터 비교를 기반으로 이 종목의 재무 건전성을 심층 분석해 주세요. 동종 업계 대비 강점과 약점을 명확히 구분해 주세요.",
        )

        private val STOCK_PRESETS_EN = mapOf(
            "summary" to "Provide a comprehensive summary analysis of this stock's current state using all the data above. Consider technical indicators, fundamentals, risk badge, and factor exposure to highlight key points.",
            "beginner" to "Explain this stock's current state in simple terms that a beginner investor can understand. Add brief explanations in parentheses for technical terms.",
            "risk_assessment" to "Provide a focused risk analysis of this stock based on the data above. Evaluate volatility, technical overbought/oversold conditions, financial soundness, and factor risks comprehensively.",
            "financial_health" to "Provide an in-depth financial health analysis based on the financial data and sector comparison above. Clearly distinguish strengths and weaknesses relative to sector peers.",
        )

        private val PORTFOLIO_PRESETS_KO = mapOf(
            "diagnosis" to "위 포트폴리오 데이터를 종합하여 포트폴리오의 현재 상태를 진단해 주세요. 리스크 점수, 분산도, 종목 구성의 강점과 약점을 분석해 주세요.",
            "reduce_risk" to "위 포트폴리오의 리스크를 줄이기 위한 구체적인 방안을 제안해 주세요. 현재 리스크 분해 결과와 종목별 베타, 변동성을 고려해 주세요.",
            "outlook" to "위 포트폴리오의 현재 구성을 바탕으로 향후 전망을 분석해 주세요. 각 종목의 기술적 지표와 팩터 노출을 참고하여 시장 환경 변화에 따른 시나리오를 제시해 주세요.",
            "aggressive" to "위 포트폴리오를 공격적 수익 추구 관점에서 분석해 주세요. 각 종목의 모멘텀, 펀더멘털 강도, 업종 내 위치를 고려하여 비중 조정 방안을 제시해 주세요.",
            "financial_weakness" to "위 포트폴리오 내 종목들의 재무적 취약점을 심층 분석해 주세요. 각 종목의 부채비율, ROE, 영업이익률을 섹터 중간값과 비교하여 재무적으로 취약한 종목을 식별해 주세요.",
        )

        private val PORTFOLIO_PRESETS_EN = mapOf(
            "diagnosis" to "Diagnose the current state of this portfolio using all the data above. Analyze the risk score, diversification, and strengths/weaknesses of the holdings composition.",
            "reduce_risk" to "Suggest specific measures to reduce this portfolio's risk. Consider the current risk decomposition results and per-holding beta and volatility.",
            "outlook" to "Analyze the forward outlook based on this portfolio's current composition. Reference each holding's technical indicators and factor exposure to present scenarios under different market conditions.",
            "aggressive" to "Analyze this portfolio from an aggressive return-seeking perspective. Consider each holding's momentum, fundamental strength, and sector positioning to suggest weight adjustments.",
            "financial_weakness" to "Provide an in-depth analysis of financial weaknesses among the holdings in this portfolio. Compare each holding's debt ratio, ROE, and operating margin against sector medians to identify financially vulnerable stocks.",
        )
    }
}
