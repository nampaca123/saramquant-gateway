package me.saramquantgateway.feature.recommendation.service

import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.core.JsonValue
import org.springframework.stereotype.Component

@Component
class RecommendationToolDefinitions {

    fun all(): List<ToolUnion> = listOf(
        ToolUnion.ofTool(screenStocks()),
        ToolUnion.ofTool(getStockDetail()),
        ToolUnion.ofTool(getSectorOverview()),
        ToolUnion.ofTool(evaluatePortfolio()),
        ToolUnion.ofWebSearchTool20250305(WebSearchTool20250305.builder().maxUses(3).build()),
    )

    private fun screenStocks() = Tool.builder()
        .name("screen_stocks")
        .description("Screen stocks from the database using filters. Returns a list of stocks with key metrics (symbol, name, sector, risk tier, beta, sharpe, PER, PBR, ROE, debt ratio, latest price, change%). Use this to explore the investment universe and find candidates matching the user's risk profile.")
        .inputSchema(Tool.InputSchema.builder()
            .type(JsonValue.from("object"))
            .properties(JsonValue.from(mapOf(
                "market" to mapOf("type" to "string", "enum" to listOf("KR_KOSPI", "KR_KOSDAQ", "US_NYSE", "US_NASDAQ"), "description" to "Stock market to search"),
                "tiers" to mapOf("type" to "array", "items" to mapOf("type" to "string", "enum" to listOf("VERY_LOW", "LOW", "MODERATE", "HIGH", "VERY_HIGH")), "description" to "Risk tier filter"),
                "sector" to mapOf("type" to "string", "description" to "Sector name filter"),
                "beta_min" to mapOf("type" to "number"), "beta_max" to mapOf("type" to "number"),
                "sharpe_min" to mapOf("type" to "number"),
                "roe_min" to mapOf("type" to "number"),
                "debt_ratio_max" to mapOf("type" to "number"),
                "per_max" to mapOf("type" to "number"),
                "sort" to mapOf("type" to "string", "enum" to listOf("sharpe_desc", "beta_asc", "roe_desc", "per_asc", "name_asc"), "description" to "Sort order"),
                "size" to mapOf("type" to "integer", "description" to "Number of results (default 20, max 40)"),
            )))
            .required(JsonValue.from(listOf("market")))
            .build())
        .build()

    private fun getStockDetail() = Tool.builder()
        .name("get_stock_detail")
        .description("Get comprehensive data for a specific stock: technical indicators (RSI, MACD, BB, ATR, Beta, Sharpe), fundamentals (PER, PBR, ROE, debt ratio), factor exposures (size, value, momentum, volatility, quality, leverage z-scores), risk badge, sector comparison, and risk-free rate. Factor exposures are critical for assessing diversification — stocks with similar factor profiles are correlated.")
        .inputSchema(Tool.InputSchema.builder()
            .type(JsonValue.from("object"))
            .properties(JsonValue.from(mapOf(
                "stock_id" to mapOf("type" to "integer", "description" to "Stock ID from screen_stocks results"),
            )))
            .required(JsonValue.from(listOf("stock_id")))
            .build())
        .build()

    private fun getSectorOverview() = Tool.builder()
        .name("get_sector_overview")
        .description("Get sector-level aggregate statistics for a market: number of stocks, median PER, PBR, ROE, operating margin, and debt ratio per sector. Use this to understand which sectors are cheap/expensive and to plan sector allocation.")
        .inputSchema(Tool.InputSchema.builder()
            .type(JsonValue.from("object"))
            .properties(JsonValue.from(mapOf(
                "market" to mapOf("type" to "string", "enum" to listOf("KR_KOSPI", "KR_KOSDAQ", "US_NYSE", "US_NASDAQ"), "description" to "Market to analyze"),
            )))
            .required(JsonValue.from(listOf("market")))
            .build())
        .build()

    private fun evaluatePortfolio() = Tool.builder()
        .name("evaluate_portfolio")
        .description("Evaluate a candidate portfolio's risk using the factor model. Input a list of stock_ids with weights (0-1, summing to 1.0). Returns: portfolio factor exposure (weighted z-scores across 6 factors), estimated annual volatility from factor covariance matrix, concentration metrics (HHI, effective N, sector distribution), weighted average metrics (beta, sharpe, PER, ROE, debt ratio), and warnings for factor tilts exceeding ±1.0σ. This is the KEY tool for verifying diversification — always use it before finalizing a recommendation.")
        .inputSchema(Tool.InputSchema.builder()
            .type(JsonValue.from("object"))
            .properties(JsonValue.from(mapOf(
                "stocks" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "stock_id" to mapOf("type" to "integer"),
                            "weight" to mapOf("type" to "number", "description" to "Allocation weight 0-1"),
                        ),
                        "required" to listOf("stock_id", "weight"),
                    ),
                    "description" to "Portfolio stocks with weights summing to 1.0",
                ),
            )))
            .required(JsonValue.from(listOf("stocks")))
            .build())
        .build()

}
