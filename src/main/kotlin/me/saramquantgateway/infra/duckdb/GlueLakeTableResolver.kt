package me.saramquantgateway.infra.duckdb

import com.github.benmanes.caffeine.cache.Caffeine
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.GetTableRequest
import java.time.Duration

// Glue 카탈로그의 metadata_location을 5분 캐시해 iceberg_scan 표현식으로 반환한다.
class GlueLakeTableResolver(
    private val glueClient: GlueClient,
    private val database: String,
    ttl: Duration = Duration.ofMinutes(5),
) : LakeTableResolver {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .maximumSize(MAX_CACHED_TABLES)
        .build<String, String>()

    override fun ref(table: String): String = cache.get(table) { resolve(it) }

    private fun resolve(table: String): String {
        val response = try {
            glueClient.getTable(GetTableRequest.builder().databaseName(database).name(table).build())
        } catch (e: Exception) {
            throw IllegalStateException("Failed to resolve Glue table '$database.$table': ${e.message}", e)
        }
        val location = response.table()?.parameters()?.get("metadata_location")
        if (location.isNullOrBlank()) {
            throw IllegalStateException("Glue table '$database.$table' has no metadata_location parameter")
        }
        return "iceberg_scan('$location')"
    }

    private companion object {
        const val MAX_CACHED_TABLES = 200L
    }
}
