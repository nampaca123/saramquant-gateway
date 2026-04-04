package me.saramquantgateway.feature.recommendation.service

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.*
import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.entity.recommendation.PortfolioRecommendation
import me.saramquantgateway.domain.repository.recommendation.PortfolioRecommendationRepository
import me.saramquantgateway.feature.portfolio.dto.PortfolioDetail
import me.saramquantgateway.feature.recommendation.dto.ProgressEvent
import me.saramquantgateway.feature.recommendation.dto.RecommendationRequest
import me.saramquantgateway.feature.recommendation.dto.RecommendationResponse
import me.saramquantgateway.feature.recommendation.dto.RecommendedStock
import me.saramquantgateway.infra.llm.config.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

@Service
class RecommendationAgentService(
    private val toolExecutor: RecommendationToolExecutor,
    private val toolDefs: RecommendationToolDefinitions,
    private val recRepo: PortfolioRecommendationRepository,
    private val props: LlmProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun recommend(req: RecommendationRequest, portfolio: PortfolioDetail, userId: UUID, emitter: SseEmitter) {
        try {
            val client = AnthropicOkHttpClient.builder().apiKey(props.claude.apiKey).build()
            val messages = mutableListOf<MessageParam>(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(RecommendationPrompts.userMessage(portfolio, req.lang, req.message))
                    .build()
            )

            var toolCallCount = 0
            val maxIterations = 10

            for (iteration in 1..maxIterations) {
                val response = client.messages().create(MessageCreateParams.builder()
                    .model(props.recommendationModel)
                    .maxTokens(4096)
                    .system(RecommendationPrompts.system(req.lang))
                    .messages(messages)
                    .tools(toolDefs.all())
                    .build())

                val toolUseBlocks = response.content().mapNotNull { block -> block.toolUse().orElse(null) }
                val hasWebSearch = response.content().any { block -> block.webSearchToolResult().isPresent }
                val textParts = response.content().mapNotNull { block -> block.text().orElse(null) }

                if (hasWebSearch) {
                    emitToolProgress(emitter, "web_search", null, req.lang)
                    toolCallCount++
                }

                if (toolUseBlocks.isEmpty()) {
                    val text = textParts.joinToString("") { tb -> tb.text() }
                    emitStepProgress(emitter, "BUILDING_RECOMMENDATION", req.lang)
                    val result = parseAndSave(text, req, userId, toolCallCount)
                    emitter.send(SseEmitter.event().name("result")
                        .data(objectMapper.writeValueAsString(result)))
                    emitter.complete()
                    return
                }

                messages.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(response.content().map { block -> block.toParam() })
                    .build())

                val toolResults = mutableListOf<ToolResultBlockParam>()
                for (toolUse in toolUseBlocks) {
                    @Suppress("UNCHECKED_CAST")
                    val input = toolUse._input().convert(Map::class.java) as Map<String, Any?>
                    val stockName = toolExecutor.resolveStockName(toolUse.name(), input)
                    emitToolProgress(emitter, toolUse.name(), stockName, req.lang)
                    val result = toolExecutor.execute(toolUse.name(), input)
                    toolCallCount++
                    toolResults.add(ToolResultBlockParam.builder()
                        .toolUseId(toolUse.id())
                        .content(result)
                        .build())
                }

                messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults.map { tr -> ContentBlockParam.ofToolResult(tr) })
                    .build())
            }

            emitter.send(SseEmitter.event().name("error")
                .data("""{"message":"Agent exceeded max iterations"}"""))
            emitter.complete()
        } catch (e: Exception) {
            log.error("Recommendation agent failed", e)
            try {
                emitter.send(SseEmitter.event().name("error")
                    .data("""{"message":"${e.message?.replace("\"", "'")}"}"""))
                emitter.complete()
            } catch (_: Exception) {
                emitter.completeWithError(e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAndSave(text: String, req: RecommendationRequest, userId: UUID, toolCallCount: Int): RecommendationResponse {
        val jsonStr = extractJson(text)
        val parsed = objectMapper.readValue(jsonStr, Map::class.java) as Map<String, Any?>
        val stocksList = parsed["stocks"] as? List<Map<String, Any?>> ?: emptyList()
        val overallReasoning = parsed["overall_reasoning"] as? String ?: ""
        val currentAssessment = parsed["current_assessment"] as? String

        val stocks = stocksList.mapNotNull { s ->
            try {
                RecommendedStock(
                    stockId = (s["stock_id"] as? Number)?.toLong() ?: return@mapNotNull null,
                    symbol = s["symbol"] as? String ?: return@mapNotNull null,
                    name = s["name"] as? String ?: return@mapNotNull null,
                    sector = s["sector"] as? String,
                    allocationPercent = (s["allocation_percent"] as? Number)?.toDouble() ?: 0.0,
                    action = s["action"] as? String ?: "ADD",
                    reasoning = s["reasoning"] as? String ?: "",
                )
            } catch (e: Exception) {
                log.warn("Failed to parse stock entry: {}", s, e)
                null
            }
        }

        recRepo.save(PortfolioRecommendation(
            userId = userId,
            marketGroup = req.marketGroup,
            riskTolerance = "ASSESSED",
            lang = req.lang,
            stocks = objectMapper.writeValueAsString(stocksList),
            reasoning = overallReasoning,
            model = props.recommendationModel,
        ))

        return RecommendationResponse(
            stocks = stocks,
            overallReasoning = overallReasoning,
            currentAssessment = currentAssessment,
            model = props.recommendationModel,
            toolCallCount = toolCallCount,
        )
    }

    private fun extractJson(text: String): String {
        val fenced = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*```", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1)
        if (fenced != null) return fenced

        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) return text.substring(braceStart, braceEnd + 1)

        return text.trim()
    }

    private fun emitToolProgress(emitter: SseEmitter, toolName: String, stockName: String?, lang: String) {
        val (step, message) = when (toolName) {
            "web_search" -> "SEARCHING_MARKET" to if (lang == "en") "Researching latest market trends..." else "최신 시장 동향을 조사하고 있습니다..."
            "get_sector_overview" -> "ANALYZING_SECTORS" to if (lang == "en") "Analyzing sector overview..." else "섹터별 현황을 분석하고 있습니다..."
            "screen_stocks" -> "SCREENING_STOCKS" to if (lang == "en") "Screening stocks matching your risk profile..." else "리스크 성향에 맞는 종목을 검색하고 있습니다..."
            "get_stock_detail" -> "ANALYZING_STOCK" to if (lang == "en") "Analyzing ${stockName ?: "stock"} in detail..." else "${stockName ?: "종목"}의 상세 데이터를 확인하고 있습니다..."
            "evaluate_portfolio" -> "EVALUATING_PORTFOLIO" to if (lang == "en") "Evaluating portfolio risk..." else "포트폴리오 리스크를 검증하고 있습니다..."
            else -> "PROCESSING" to if (lang == "en") "Processing..." else "처리 중..."
        }
        emitProgress(emitter, step, message)
    }

    private fun emitStepProgress(emitter: SseEmitter, step: String, lang: String) {
        val message = if (step == "BUILDING_RECOMMENDATION") {
            if (lang == "en") "Building portfolio recommendation..." else "포트폴리오 구성 제안을 정리하고 있습니다..."
        } else step
        emitProgress(emitter, step, message)
    }

    private fun emitProgress(emitter: SseEmitter, step: String, message: String) {
        try {
            emitter.send(SseEmitter.event().name("progress")
                .data(objectMapper.writeValueAsString(ProgressEvent(step, message))))
        } catch (e: Exception) {
            log.warn("Failed to send SSE progress event: {}", e.message)
        }
    }
}
