package me.saramquantgateway.feature.ai.dto

data class StockAnalysisRequest(
    val symbol: String,
    val market: String,
    val preset: String = "summary",
    val lang: String = "ko",
)

data class PortfolioAnalysisRequest(
    val portfolioId: Long,
    val preset: String = "diagnosis",
    val lang: String = "ko",
)

data class AiAnalysisResponse(
    val analysis: String,
    val model: String,
    val cached: Boolean,
    val disclaimer: String,
) {
    companion object {
        fun disclaimer(lang: String): String = when (lang) {
            "en" -> "This analysis is for informational purposes only and does not constitute investment advice."
            else -> "이 분석은 투자 조언이 아닌 참고 자료입니다. 투자 판단의 책임은 본인에게 있습니다."
        }
    }
}

data class AiUsageResponse(
    val used: Int,
    val limit: Int,
    val date: String,
)
