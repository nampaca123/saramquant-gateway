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
대상 독자: 금융 지식이 거의 없는 2030 사회초년생. 쉬운 일상 표현을 사용하세요.

규칙:
- 주어진 데이터만 근거로 분석. 추측/외부 정보 금지.
- 수치 인용 시 정확한 값 사용.
- 긍정/부정 요인을 균형 있게.
- 전문 용어를 쓸 때는 반드시 바로 뒤에 일상적 설명을 붙일 것. 예: "PER 9.5배 (주가가 이익의 몇 배인지)"
- 면책 문구(투자 추천 아님 등)는 쓰지 말 것. 앱 UI에 이미 있음.
- 한국어로 작성.

마크다운 형식 (반드시 준수):
- 섹션 제목: ## 제목 (이모지 쓰지 말 것)
- 본문: - (하이픈 리스트). 한 항목 한 줄.
- **굵은 글씨**로 핵심 수치 강조.
- 섹션 사이 빈 줄 하나.
- 전체 분량: 300~500자.

리스크 뱃지(자체 지표): summaryTier(VERY_LOW~VERY_HIGH), dimension 0~100(높을수록 위험) — volatility/valuation/leverage/momentum/liquidity"""

        private const val SYSTEM_EN = """You are SaramQuant's financial analysis AI.
Target reader: young adults in their 20s–30s with little financial knowledge. Use plain, everyday language.

Rules:
- Use only provided data. No speculation or external info.
- Cite exact values.
- Balance positive/negative factors.
- When using a technical term, immediately follow it with a plain-English explanation. e.g. "PER of 9.5 (how many years of profit the price equals)"
- Do NOT include disclaimers (not investment advice, etc.). The app UI already shows one.
- Respond in English.

Markdown format (must follow):
- Section titles: ## Title (no emojis)
- Body: - (hyphen lists). One point per line.
- **Bold** key numbers.
- One blank line between sections.
- Total length: 300–500 words.

Risk Badge (proprietary): summaryTier(VERY_LOW~VERY_HIGH), dimensions 0–100(higher=riskier) — volatility/valuation/leverage/momentum/liquidity"""

        private val STOCK_PRESETS_KO = mapOf(
            "summary" to "위 데이터를 종합하여 이 종목의 현재 상태를 요약해 주세요. 주가 흐름, 이 회사의 재무 상태, 위험 수준, 같은 업종 대비 위치를 쉬운 말로 정리해 주세요.",
            "beginner" to """위 종목의 현재 상태를 금융 지식이 전혀 없는 사람(초등학생 수준)도 이해할 수 있게 설명해 주세요.
절대 규칙:
- RSI, PER, PBR, ROE, 베타 같은 용어를 직접 쓰지 말고, "이 주식이 최근 많이 올라서 쉬어갈 수 있다" 같은 일상 표현으로 바꿀 것.
- 숫자는 꼭 필요한 것만 (현재가, 등락률 정도). 나머지는 "높다/낮다/보통이다"로.
- "쉽게 말하면", "예를 들면" 같은 연결어를 적극 사용.""",
            "risk_assessment" to "위 데이터를 기반으로 이 종목의 위험 요소를 집중 분석해 주세요. 주가가 얼마나 출렁이는지, 최근 너무 많이 올라서 조정 가능성은 없는지, 빚이 많지는 않은지, 어떤 상황에서 손해를 볼 수 있는지를 쉬운 표현으로 정리해 주세요.",
            "financial_health" to "위 재무 데이터와 같은 업종 비교를 바탕으로 이 회사의 재정 상태를 분석해 주세요. 같은 업종 다른 회사들과 비교해서 이 회사가 잘하고 있는 점, 걱정되는 점을 명확히 구분해 주세요.",
        )

        private val STOCK_PRESETS_EN = mapOf(
            "summary" to "Summarize this stock's current state using all the data above. Cover price trend, financial health, risk level, and how it compares to peers — in plain, everyday language.",
            "beginner" to """Explain this stock's current state so that someone with zero financial knowledge can understand.
Absolute rules:
- Do NOT use terms like RSI, PER, PBR, ROE, beta directly. Instead say things like "this stock has gone up a lot recently, so it might take a breather."
- Only include essential numbers (current price, % change). For everything else, say "high / low / about average."
- Use connectors like "in other words," "think of it like" frequently.""",
            "risk_assessment" to "Analyze this stock's risks based on the data above. How much does the price swing? Has it risen too fast and might pull back? Does the company owe a lot? Under what conditions could you lose money? Use everyday language.",
            "financial_health" to "Analyze this company's financial health based on the data and sector comparison above. Compared to other companies in the same industry, what is this company doing well, and what looks worrying? Make the distinction clear.",
        )

        private val PORTFOLIO_PRESETS_KO = mapOf(
            "diagnosis" to "위 포트폴리오 데이터를 종합하여 현재 상태를 진단해 주세요. 위험 점수, 종목이 얼마나 다양하게 분산되어 있는지, 전체 구성의 강점과 약점을 쉬운 말로 분석해 주세요.",
            "reduce_risk" to "위 포트폴리오의 위험을 줄이려면 어떻게 해야 할지 구체적으로 제안해 주세요. 어떤 종목이 시장 변동에 특히 민감한지, 쏠림이 있는지를 쉽게 설명해 주세요.",
            "outlook" to "위 포트폴리오 구성을 바탕으로 앞으로 어떤 일이 일어날 수 있는지 분석해 주세요. 시장이 좋을 때와 안 좋을 때 각각 어떤 영향을 받을지 쉽게 설명해 주세요.",
            "aggressive" to "위 포트폴리오를 수익을 더 키우는 관점에서 분석해 주세요. 각 종목의 상승 흐름, 기본 체력, 업종 내 위치를 고려해서 비중을 어떻게 바꾸면 좋을지 제안해 주세요.",
            "financial_weakness" to "위 포트폴리오 종목들 중 재정적으로 걱정되는 회사가 있는지 분석해 주세요. 빚이 많거나, 돈을 잘 못 벌거나, 같은 업종 대비 뒤처지는 종목이 있으면 쉬운 말로 알려 주세요.",
        )

        private val PORTFOLIO_PRESETS_EN = mapOf(
            "diagnosis" to "Diagnose this portfolio's current state using all the data above. Analyze the risk score, how well-diversified it is, and the strengths/weaknesses of the mix — in plain language.",
            "reduce_risk" to "Suggest specific ways to make this portfolio less risky. Which stocks are most sensitive to market swings? Is there too much concentration? Explain simply.",
            "outlook" to "Based on this portfolio's current mix, what could happen next? Describe scenarios for both good and bad market conditions in everyday language.",
            "aggressive" to "Analyze this portfolio from a growth-seeking perspective. Consider each stock's momentum, financial strength, and position in its industry to suggest how to adjust the weights.",
            "financial_weakness" to "Are there any financially worrying companies in this portfolio? Identify stocks with lots of debt, poor earnings, or lagging behind their industry — explain in plain language.",
        )
    }
}
