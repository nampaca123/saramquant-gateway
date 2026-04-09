package me.saramquantgateway.feature.recommendation.service

import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolUnion
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.core.JsonValue
import org.springframework.stereotype.Component

@Component
class RecommendationToolDefinitions {

    fun all(): List<ToolUnion> = listOf(
        ToolUnion.ofTool(findCandidates()),
        ToolUnion.ofTool(validatePortfolio()),
        ToolUnion.ofWebSearchTool20250305(WebSearchTool20250305.builder().maxUses(2).build()),
    )

    private fun findCandidates() = Tool.builder()
        .name("find_candidates")
        .description("""Search for candidate stocks matching your investment thesis. Returns enriched results with factor exposures and sector comparisons — no follow-up detail lookups needed.

Numeric filters (beta, sharpe, ROE, debt ratio, risk tiers) are automatically applied based on the user's direction. You only choose WHAT to search for (sectors, sorting), not HOW to filter.

Example: {"market": "KR", "sectors": ["Technology", "Healthcare"], "sort": "sharpe_desc", "limit": 8}
Example: {"market": "US", "exclude_sectors": ["Utilities"], "exclude_stock_ids": [123, 456]}""")
        .inputSchema(Tool.InputSchema.builder()
            .type(JsonValue.from("object"))
            .properties(JsonValue.from(mapOf(
                "market" to mapOf("type" to "string", "enum" to listOf("KR", "US"), "description" to "Market group"),
                "sectors" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Include only these sectors"),
                "exclude_sectors" to mapOf("type" to "array", "items" to mapOf("type" to "string"), "description" to "Exclude these sectors"),
                "exclude_stock_ids" to mapOf("type" to "array", "items" to mapOf("type" to "integer"), "description" to "Stock IDs to exclude (e.g. existing holdings)"),
                "sort" to mapOf("type" to "string", "enum" to listOf("sharpe_desc", "beta_asc", "roe_desc", "per_asc"), "description" to "Sort order (default: sharpe_desc)"),
                "limit" to mapOf("type" to "integer", "description" to "Number of results (default 8, max 15)"),
            )))
            .required(JsonValue.from(listOf("market")))
            .build())
        .build()

    private fun validatePortfolio() = Tool.builder()
        .name("validate_portfolio")
        .description("""Validate a proposed portfolio's risk using the factor model. Input stock_ids with weights (0-1, sum=1.0). Returns: factor exposure, estimated volatility, concentration (HHI), weighted metrics, and warnings for factor tilts ±1.0σ. Always call this before finalizing.

Example: {"stocks": [{"stock_id": 123, "weight": 0.3}, {"stock_id": 456, "weight": 0.3}, {"stock_id": 789, "weight": 0.4}]}""")
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
