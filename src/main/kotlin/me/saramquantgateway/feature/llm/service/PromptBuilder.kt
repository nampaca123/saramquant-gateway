package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.feature.stock.dto.FactorExposureSnapshot
import me.saramquantgateway.feature.stock.dto.FinancialStatementSnapshot
import me.saramquantgateway.feature.stock.dto.FundamentalSnapshot
import me.saramquantgateway.feature.stock.dto.IndicatorSnapshot
import me.saramquantgateway.feature.stock.dto.SectorComparisonSnapshot
import org.springframework.stereotype.Component
import java.math.BigDecimal

data class StockContextData(
    val name: String, val symbol: String, val market: String, val sector: String?,
    val close: BigDecimal?, val priceChange: Double?, val dataDate: String?,
    val weekReturn: Double?, val monthReturn: Double?, val threeMonthReturn: Double?,
    val week52High: BigDecimal?, val week52Low: BigDecimal?,
    val marketCap: BigDecimal?,
    val badge: Map<String, Any>?, val summaryTier: String?,
    val indicator: IndicatorSnapshot?,
    val fundamental: FundamentalSnapshot?,
    val sectorComparison: SectorComparisonSnapshot?,
    val factorExposure: FactorExposureSnapshot?,
    val financialStatement: FinancialStatementSnapshot?,
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
        val en = lang == "en"

        sb.appendLine(if (en) "=== Stock Data ===" else "=== 종목 데이터 ===")
        sb.appendLine(kv("Name" to d.name, "Symbol" to d.symbol, "Market" to d.market, "Sector" to d.sector))
        sb.appendLine(kv("Close" to d.close?.toPlainString(), "Change%" to fmt(d.priceChange), "Date" to d.dataDate))
        sb.appendLine(kv("1W" to fmt(d.weekReturn), "1M" to fmt(d.monthReturn), "3M" to fmt(d.threeMonthReturn)))
        if (d.week52High != null || d.week52Low != null) {
            sb.appendLine(kv("52wHigh" to d.week52High?.toPlainString(), "52wLow" to d.week52Low?.toPlainString()))
        }
        d.marketCap?.let { sb.appendLine(if (en) "MarketCap: ${it.toPlainString()}" else "시가총액: ${it.toPlainString()}") }

        d.summaryTier?.let { tier ->
            sb.appendLine(if (en) "Risk Tier: $tier" else "리스크 등급: $tier")
            d.badge?.let { badge ->
                val dims = badge.entries.joinToString(", ") { (k, v) -> "$k=$v" }
                sb.appendLine(if (en) "Risk Scores (0=safe, 100=risky): $dims" else "리스크 세부 (0=안전, 100=위험): $dims")
            }
        }

        d.indicator?.let { i ->
            sb.appendLine()
            sb.appendLine(if (en) "--- Technical (${i.date}) ---" else "--- 기술적 지표 (${i.date}) ---")
            sb.appendLine(kv("RSI14" to dec(i.rsi14), "MACD" to dec(i.macd), "Signal" to dec(i.macdSignal), "Hist" to dec(i.macdHist)))
            sb.appendLine(kv("StochK" to dec(i.stochK), "StochD" to dec(i.stochD), "ADX" to dec(i.adx14), "+DI" to dec(i.plusDi), "-DI" to dec(i.minusDi)))
            sb.appendLine(kv("BB" to "${dec(i.bbUpper)}/${dec(i.bbMiddle)}/${dec(i.bbLower)}", "ATR" to dec(i.atr14)))
            sb.appendLine(kv("SMA20" to dec(i.sma20), "EMA20" to dec(i.ema20), "SAR" to dec(i.sar)))
            sb.appendLine(kv("Beta" to dec(i.beta), "Alpha" to dec(i.alpha), "Sharpe" to dec(i.sharpe)))
        }

        d.fundamental?.let { f ->
            sb.appendLine()
            sb.appendLine(if (en) "--- Fundamentals (${f.date}) ---" else "--- 펀더멘털 (${f.date}) ---")
            sb.appendLine(kv("PER" to dec(f.per), "PBR" to dec(f.pbr), "EPS" to dec(f.eps), "BPS" to dec(f.bps)))
            sb.appendLine(kv("ROE" to dec(f.roe), "DebtRatio" to dec(f.debtRatio), "OpMargin" to dec(f.operatingMargin)))
        }

        d.financialStatement?.let { fs ->
            sb.appendLine()
            sb.appendLine(if (en) "--- Financial Statement (FY${fs.fiscalYear}) ---" else "--- 재무제표 (FY${fs.fiscalYear}) ---")
            sb.appendLine(kv("Revenue" to dec(fs.revenue), "OpIncome" to dec(fs.operatingIncome), "NetIncome" to dec(fs.netIncome)))
            sb.appendLine(kv("TotalAssets" to dec(fs.totalAssets), "TotalEquity" to dec(fs.totalEquity)))
            if (fs.revenueGrowthPct != null || fs.netIncomeGrowthPct != null) {
                sb.appendLine(kv("RevenueGrowth%" to fmt(fs.revenueGrowthPct), "NetIncomeGrowth%" to fmt(fs.netIncomeGrowthPct)))
            }
        }

        d.sectorComparison?.let { s ->
            sb.appendLine()
            sb.appendLine(if (en) "--- Sector: ${s.sector} (${s.stockCount} stocks) ---" else "--- 섹터: ${s.sector} (${s.stockCount}개) ---")
            sb.appendLine(kv("MedPER" to dec(s.medianPer), "MedPBR" to dec(s.medianPbr), "MedROE" to dec(s.medianRoe)))
            sb.appendLine(kv("MedOpMargin" to dec(s.medianOperatingMargin), "MedDebtRatio" to dec(s.medianDebtRatio)))
        }

        d.factorExposure?.let { f ->
            sb.appendLine()
            sb.appendLine(if (en) "--- Factor Exposure (${f.date}) ---" else "--- 팩터 노출 (${f.date}) ---")
            sb.appendLine(kv("Size" to dec(f.sizeZ), "Value" to dec(f.valueZ), "Momentum" to dec(f.momentumZ)))
            sb.appendLine(kv("Volatility" to dec(f.volatilityZ), "Quality" to dec(f.qualityZ), "Leverage" to dec(f.leverageZ)))
        }

        d.riskFreeRate?.let {
            sb.appendLine()
            sb.appendLine(if (en) "RiskFreeRate: ${it.toPlainString()}%" else "무위험이자율: ${it.toPlainString()}%")
        }

        return sb.toString().trimEnd()
    }

    private fun kv(vararg pairs: Pair<String, String?>): String =
        pairs.filter { it.second != null }.joinToString(", ") { "${it.first}: ${it.second}" }

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
        private const val SYSTEM_KO = """당신은 사람퀀트(SaramQuant)의 금융 분석 AI입니다.
규칙:
- 주어진 데이터만 근거로 분석. 추측/외부 정보 금지.
- 수치 인용 시 정확한 값 사용.
- 마크다운 형식(##, **, - 등) 사용. 단, 간결하게 — 핵심만 요약하여 전체 500자 이내로 작성.
- 긍정/부정 요인을 균형 있게.
- 투자 추천이 아닌 데이터 기반 참고 자료임을 한 줄로 명시.
- 한국어로 작성.

리스크 뱃지(자체 지표): summaryTier(VERY_LOW~VERY_HIGH), dimension 0~100(높을수록 위험) — volatility/valuation/leverage/momentum/liquidity"""

        private const val SYSTEM_EN = """You are SaramQuant's financial analysis AI.
Rules:
- Use only provided data. No speculation or external info.
- Cite exact values.
- Use markdown (##, **, -). Keep it concise — summarize key points in under 500 words.
- Balance positive/negative factors.
- State this is data-driven analysis, not investment advice, in one line.
- Respond in English.

Risk Badge (proprietary): summaryTier(VERY_LOW~VERY_HIGH), dimensions 0–100(higher=riskier) — volatility/valuation/leverage/momentum/liquidity"""

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
