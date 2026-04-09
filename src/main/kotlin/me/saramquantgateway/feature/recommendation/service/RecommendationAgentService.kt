package me.saramquantgateway.feature.recommendation.service

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.*
import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.entity.recommendation.PortfolioRecommendation
import me.saramquantgateway.domain.entity.user.UserProfile
import me.saramquantgateway.domain.repository.recommendation.PortfolioRecommendationRepository
import me.saramquantgateway.feature.portfolio.dto.PortfolioDetail
import me.saramquantgateway.feature.recommendation.dto.*
import me.saramquantgateway.infra.llm.config.LlmProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

@Service
class RecommendationAgentService(
    private val toolExecutor: RecommendationToolExecutor,
    private val toolDefs: RecommendationToolDefinitions,
    private val recRepo: PortfolioRecommendationRepository,
    private val props: LlmProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(props.claude.apiKey)
            .timeout(props.claude.timeout)
            .build()
    }

    companion object {
        private const val MAX_ITERATIONS = 7
        private const val MAX_API_RETRIES = 2
        private const val MAX_TOOL_RESULT_CHARS = 6000
        private const val THINKING_FLUSH_MS = 200L
        private const val HEARTBEAT_INTERVAL_MS = 5000L
    }

    private data class AccumulatedToolUse(val id: String, val name: String, val inputJson: String)

    private class StreamedBlock(
        val index: Long,
        val type: String,
        val toolId: String? = null,
        val toolName: String? = null,
        val textBuilder: StringBuilder = StringBuilder(),
        val inputJsonBuilder: StringBuilder = StringBuilder(),
        var serverParam: ContentBlockParam? = null,
    )

    private data class StreamResult(
        val contentBlockParams: List<ContentBlockParam>,
        val toolUses: List<AccumulatedToolUse>,
        val accumulatedText: String,
        val hasWebSearch: Boolean,
    )

    fun recommend(
        req: RecommendationRequest,
        portfolio: PortfolioDetail,
        userId: UUID,
        profile: UserProfile?,
        emitter: SseEmitter,
    ): Boolean {
        val cancelled = AtomicBoolean(false)
        emitter.onCompletion { cancelled.set(true) }
        emitter.onTimeout { cancelled.set(true) }
        emitter.onError { cancelled.set(true) }

        try {
            val messages = mutableListOf(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .content(RecommendationPrompts.userMessage(portfolio, req.lang, req.direction, profile))
                    .build()
            )

            var toolCallCount = 0
            var lastText = ""

            for (iteration in 1..MAX_ITERATIONS) {
                if (cancelled.get()) {
                    log.info("Client disconnected at iteration {}", iteration)
                    return false
                }

                val params = MessageCreateParams.builder()
                    .model(props.recommendationModel)
                    .maxTokens(4096)
                    .system(RecommendationPrompts.system(req.lang))
                    .messages(messages)
                    .tools(toolDefs.all())
                    .build()

                val sr = streamWithRetry(params, emitter, cancelled, req.lang)

                if (cancelled.get()) {
                    log.info("Client disconnected after streaming at iteration {}", iteration)
                    return false
                }

                lastText = sr.accumulatedText
                if (sr.hasWebSearch) toolCallCount++

                if (sr.toolUses.isEmpty()) {
                    emitStepProgress(emitter, "BUILDING_RECOMMENDATION", req.lang)
                    val result = parseAndSave(lastText, req, userId, toolCallCount)
                    emitter.send(SseEmitter.event().name("result")
                        .data(objectMapper.writeValueAsString(result)))
                    emitter.complete()
                    return true
                }

                messages.add(MessageParam.builder()
                    .role(MessageParam.Role.ASSISTANT)
                    .contentOfBlockParams(sr.contentBlockParams)
                    .build())

                val toolResults = executeToolsParallel(sr.toolUses, emitter, cancelled, req.lang)
                toolCallCount += sr.toolUses.size

                if (cancelled.get()) return false

                messages.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults.map { ContentBlockParam.ofToolResult(it) })
                    .build())
            }

            return handleMaxIterations(lastText, req, userId, toolCallCount, emitter, req.lang)
        } catch (e: Exception) {
            log.error("Recommendation agent failed", e)
            emitError(emitter, e.message ?: "Internal error")
            return false
        }
    }

    // ── Streaming ──

    private fun streamWithRetry(
        params: MessageCreateParams,
        emitter: SseEmitter,
        cancelled: AtomicBoolean,
        lang: String,
    ): StreamResult {
        for (attempt in 1..MAX_API_RETRIES) {
            try {
                return processStream(params, emitter, cancelled, lang)
            } catch (e: Exception) {
                if (attempt == MAX_API_RETRIES) throw e
                log.warn("Claude streaming attempt {}/{} failed: {}", attempt, MAX_API_RETRIES, e.message)
                Thread.sleep((1L shl attempt) * 1000)
            }
        }
        throw RuntimeException("All Claude streaming attempts failed")
    }

    private fun processStream(
        params: MessageCreateParams,
        emitter: SseEmitter,
        cancelled: AtomicBoolean,
        lang: String,
    ): StreamResult {
        val blocks = mutableMapOf<Long, StreamedBlock>()
        var hasWebSearch = false
        val thinkingBuf = StringBuilder()
        var lastFlush = System.currentTimeMillis()

        client.messages().createStreaming(params).use { stream ->
            stream.stream()
                .takeWhile { !cancelled.get() }
                .forEach { event ->
                    when {
                        event.isContentBlockStart() -> {
                            val start = event.asContentBlockStart()
                            val cb = start.contentBlock()
                            val idx = start.index()
                            when {
                                cb.isToolUse() -> {
                                    val tu = cb.asToolUse()
                                    blocks[idx] = StreamedBlock(idx, "tool_use", tu.id(), tu.name())
                                }
                                cb.isText() -> blocks[idx] = StreamedBlock(idx, "text")
                                cb.isWebSearchToolResult() -> {
                                    hasWebSearch = true
                                    emitToolProgress(emitter, "web_search", null, lang)
                                    blocks[idx] = StreamedBlock(idx, "server_tool").apply {
                                        serverParam = ContentBlockParam.ofWebSearchToolResult(
                                            cb.asWebSearchToolResult().toParam()
                                        )
                                    }
                                }
                                else -> {}
                            }
                        }
                        event.isContentBlockDelta() -> {
                            val de = event.asContentBlockDelta()
                            val block = blocks[de.index()] ?: return@forEach
                            val delta = de.delta()
                            when {
                                delta.isText() -> {
                                    val text = delta.asText().text()
                                    block.textBuilder.append(text)
                                    thinkingBuf.append(text)
                                    val now = System.currentTimeMillis()
                                    if (now - lastFlush >= THINKING_FLUSH_MS) {
                                        emitThinking(emitter, thinkingBuf.toString())
                                        thinkingBuf.clear()
                                        lastFlush = now
                                    }
                                }
                                delta.isInputJson() ->
                                    block.inputJsonBuilder.append(delta.asInputJson().partialJson())
                            }
                        }
                        event.isMessageDelta() -> { /* stopReason tracked implicitly by tool_use presence */ }
                    }
                }
        }

        if (thinkingBuf.isNotEmpty()) emitThinking(emitter, thinkingBuf.toString())

        val contentBlockParams = mutableListOf<ContentBlockParam>()
        val toolUses = mutableListOf<AccumulatedToolUse>()
        val allText = StringBuilder()

        for (block in blocks.values.sortedBy { it.index }) {
            when (block.type) {
                "text" -> {
                    val text = block.textBuilder.toString()
                    allText.append(text)
                    contentBlockParams.add(
                        ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())
                    )
                }
                "tool_use" -> {
                    val json = block.inputJsonBuilder.toString()
                    toolUses.add(AccumulatedToolUse(block.toolId!!, block.toolName!!, json))
                    val parsed = try {
                        objectMapper.readValue(json, Map::class.java)
                    } catch (_: Exception) { emptyMap<String, Any?>() }
                    contentBlockParams.add(
                        ContentBlockParam.ofToolUse(
                            ToolUseBlockParam.builder()
                                .id(block.toolId)
                                .name(block.toolName)
                                .input(JsonValue.from(parsed))
                                .build()
                        )
                    )
                }
                "server_tool" -> block.serverParam?.let { contentBlockParams.add(it) }
            }
        }

        return StreamResult(contentBlockParams, toolUses, allText.toString(), hasWebSearch)
    }

    // ── Parallel tool execution ──
    // Claude batches tools in one turn only when they are independent.

    private fun executeToolsParallel(
        toolUses: List<AccumulatedToolUse>,
        emitter: SseEmitter,
        cancelled: AtomicBoolean,
        lang: String,
    ): List<ToolResultBlockParam> {
        toolUses.forEach { tu ->
            val input = parseToolInput(tu.inputJson)
            val stockName = toolExecutor.resolveStockName(tu.name, input)
            emitToolCall(emitter, tu.name, input)
            emitToolProgress(emitter, tu.name, stockName, lang)
        }

        val heartbeat = Timer("sse-hb", true)
        val hbTask = object : TimerTask() {
            override fun run() {
                try { emitter.send(SseEmitter.event().comment("heartbeat")) } catch (_: Exception) {}
            }
        }
        heartbeat.scheduleAtFixedRate(hbTask, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS)

        try {
            if (toolUses.size == 1 || cancelled.get()) {
                return toolUses.map { executeSingleTool(it, emitter) }
            }
            val futures = toolUses.map { tu ->
                CompletableFuture.supplyAsync { executeSingleTool(tu, emitter) }
            }
            return futures.map { it.join() }
        } finally {
            hbTask.cancel()
            heartbeat.cancel()
        }
    }

    private fun executeSingleTool(tu: AccumulatedToolUse, emitter: SseEmitter): ToolResultBlockParam {
        val startMs = System.currentTimeMillis()
        val input = parseToolInput(tu.inputJson)
        val result = toolExecutor.execute(tu.name, input)
        emitToolResult(emitter, tu.name, buildToolSummary(tu.name, result), System.currentTimeMillis() - startMs)
        return ToolResultBlockParam.builder()
            .toolUseId(tu.id)
            .content(truncateResult(result))
            .build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseToolInput(json: String): Map<String, Any?> = try {
        objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
    } catch (_: Exception) { emptyMap() }

    private fun buildToolSummary(name: String, result: String): String = try {
        val m = objectMapper.readValue(result, Map::class.java)
        when (name) {
            "screen_stocks" -> "${(m["stocks"] as? List<*>)?.size ?: 0} stocks found"
            "get_stock_detail" -> "${m["name"] ?: "Stock"} loaded"
            "get_sector_overview" -> "${(m["sectors"] as? List<*>)?.size ?: 0} sectors"
            "evaluate_portfolio" -> "Evaluated (${(m["warnings"] as? List<*>)?.size ?: 0} warnings)"
            else -> "Done"
        }
    } catch (_: Exception) { "Done" }

    // ── Graceful degradation ──

    private fun handleMaxIterations(
        lastText: String,
        req: RecommendationRequest,
        userId: UUID,
        toolCallCount: Int,
        emitter: SseEmitter,
        lang: String,
    ): Boolean {
        if (lastText.contains("{") && lastText.contains("stocks")) {
            try {
                val result = parseAndSave(lastText, req, userId, toolCallCount)
                emitter.send(SseEmitter.event().name("result")
                    .data(objectMapper.writeValueAsString(result)))
                emitter.complete()
                log.warn("Returning partial result at max iterations")
                return true
            } catch (_: Exception) { /* extraction failed */ }
        }
        val msg = if (lang == "en") "Analysis was too complex. Try reducing your portfolio or try again."
                  else "분석이 복잡하여 시간이 초과되었습니다. 다시 시도해 주세요."
        emitError(emitter, msg)
        return false
    }

    // ── Parse & persist ──

    private fun truncateResult(result: String): String {
        if (result.length <= MAX_TOOL_RESULT_CHARS) return result
        return result.take(MAX_TOOL_RESULT_CHARS) +
            "...(truncated, ${result.length - MAX_TOOL_RESULT_CHARS} chars omitted)"
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
        val fenced = Regex("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?})\\s*```").find(text)?.groupValues?.get(1)
        if (fenced != null) return fenced
        val start = text.indexOf('{')
        if (start < 0) return text.trim()
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return text.substring(start)
    }

    // ── SSE emitters ──

    private fun emitThinking(emitter: SseEmitter, text: String) {
        try {
            emitter.send(SseEmitter.event().name("thinking")
                .data(objectMapper.writeValueAsString(ThinkingEvent(text))))
        } catch (_: Exception) {}
    }

    private fun emitToolCall(emitter: SseEmitter, tool: String, args: Map<String, Any?>) {
        try {
            emitter.send(SseEmitter.event().name("tool_call")
                .data(objectMapper.writeValueAsString(ToolCallEvent(tool, args))))
        } catch (_: Exception) {}
    }

    private fun emitToolResult(emitter: SseEmitter, tool: String, summary: String, durationMs: Long) {
        try {
            emitter.send(SseEmitter.event().name("tool_result")
                .data(objectMapper.writeValueAsString(ToolResultEvent(tool, summary, durationMs))))
        } catch (_: Exception) {}
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
        try {
            emitter.send(SseEmitter.event().name("progress")
                .data(objectMapper.writeValueAsString(ProgressEvent(step, message))))
        } catch (e: Exception) {
            log.warn("Failed to send SSE progress: {}", e.message)
        }
    }

    private fun emitStepProgress(emitter: SseEmitter, step: String, lang: String) {
        val message = if (step == "BUILDING_RECOMMENDATION") {
            if (lang == "en") "Building portfolio recommendation..." else "포트폴리오 구성 제안을 정리하고 있습니다..."
        } else step
        try {
            emitter.send(SseEmitter.event().name("progress")
                .data(objectMapper.writeValueAsString(ProgressEvent(step, message))))
        } catch (_: Exception) {}
    }

    private fun emitError(emitter: SseEmitter, message: String) {
        try {
            emitter.send(SseEmitter.event().name("error")
                .data(objectMapper.writeValueAsString(mapOf("message" to message))))
            emitter.complete()
        } catch (_: Exception) {
            try { emitter.completeWithError(RuntimeException(message)) } catch (_: Exception) {}
        }
    }
}
