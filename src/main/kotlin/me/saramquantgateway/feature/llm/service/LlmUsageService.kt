package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.domain.store.LlmUsageStore
import me.saramquantgateway.feature.llm.dto.LlmUsageResponse
import me.saramquantgateway.infra.llm.config.LlmProperties
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class LlmUsageService(
    private val store: LlmUsageStore,
    private val props: LlmProperties,
) {
    fun checkAndIncrement(userId: UUID): Int = store.incrementBy(userId, LocalDate.now(), 1)

    fun remaining(userId: UUID): LlmUsageResponse =
        LlmUsageResponse(store.getCount(userId, LocalDate.now()), props.dailyLimit, LocalDate.now().toString())

    fun isWithinLimit(userId: UUID): Boolean = store.getCount(userId, LocalDate.now()) < props.dailyLimit

    fun checkAndIncrementBy(userId: UUID, amount: Int): Boolean {
        val today = LocalDate.now()
        if (store.getCount(userId, today) + amount > props.dailyLimit) return false
        store.incrementBy(userId, today, amount)
        return true
    }

    fun decrementBy(userId: UUID, amount: Int) {
        store.decrementBy(userId, LocalDate.now(), amount)
    }
}
