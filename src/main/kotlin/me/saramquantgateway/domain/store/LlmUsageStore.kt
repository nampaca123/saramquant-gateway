package me.saramquantgateway.domain.store

import me.saramquantgateway.infra.storage.s3.S3KvStore
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

data class LlmUsageDoc(val count: Int)

// llm-usage/{userId}/{date}.json 일일 사용량을 read-modify-write로 갱신한다.
@Component
class LlmUsageStore(private val kv: S3KvStore) {

    fun getCount(userId: UUID, date: LocalDate): Int =
        kv.get(key(userId, date), LlmUsageDoc::class.java)?.count ?: 0

    fun incrementBy(userId: UUID, date: LocalDate, amount: Int): Int = write(userId, date) { it + amount }

    fun decrementBy(userId: UUID, date: LocalDate, amount: Int): Int =
        write(userId, date) { maxOf(0, it - amount) }

    private fun write(userId: UUID, date: LocalDate, op: (Int) -> Int): Int {
        val next = op(getCount(userId, date))
        kv.put(key(userId, date), LlmUsageDoc(next))
        return next
    }

    private fun key(userId: UUID, date: LocalDate): String = "llm-usage/$userId/$date.json"
}
