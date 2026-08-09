package me.saramquantgateway.infra.storage.s3

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@JsonIgnoreProperties(ignoreUnknown = true)
data class RunSummary(@param:JsonProperty("written_at_utc") val writtenAtUtc: String? = null)

// 배치가 버킷 루트에 남기는 run-summary/<command>.json을 읽는다.
@Component
class RunSummaryReader(
    private val kvStore: S3KvStore,
    @param:Value("\${app.lake.run-summary-prefix}") private val prefix: String,
) {

    fun writtenAtUtc(command: String): String? =
        kvStore.getByAbsoluteKey("$prefix$command.json", RunSummary::class.java)?.writtenAtUtc
}
