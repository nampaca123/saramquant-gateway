package me.saramquantgateway.infra.duckdb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.EntityNotFoundException
import software.amazon.awssdk.services.glue.model.GetTableRequest
import software.amazon.awssdk.services.glue.model.GetTableResponse
import software.amazon.awssdk.services.glue.model.GetTablesRequest
import software.amazon.awssdk.services.glue.model.Table
import java.util.concurrent.atomic.AtomicInteger

class GlueLakeTableResolverTest {

    @Test
    fun `ref wraps the glue metadata location in iceberg_scan`() {
        val glue = stubGlue("s3://saramquant-bucket/warehouse/stocks/metadata/0.json")

        val ref = GlueLakeTableResolver(glue, DATABASE).ref("stocks")

        assertEquals("iceberg_scan('s3://saramquant-bucket/warehouse/stocks/metadata/0.json')", ref)
    }

    @Test
    fun `ref caches the glue lookup per table`() {
        val calls = AtomicInteger()
        val glue = stubGlue("s3://saramquant-bucket/warehouse/stocks/metadata/0.json", calls)
        val resolver = GlueLakeTableResolver(glue, DATABASE)

        repeat(5) { resolver.ref("stocks") }

        assertEquals(1, calls.get())
    }

    @Test
    fun `ref fails when the glue table has no metadata location`() {
        val glue = stubGlue(null)

        val error = runCatching { GlueLakeTableResolver(glue, DATABASE).ref("stocks") }.exceptionOrNull()

        assertTrue(error is IllegalStateException, "expected IllegalStateException but was $error")
        assertTrue(error!!.message!!.contains("metadata_location"), error.message)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
    fun `ref resolves a real glue iceberg table to the warehouse location`() {
        realGlueClient().use { glue ->
            val table = firstIcebergTable(glue)
            assumeTrue(table != null, "no iceberg table with metadata_location in glue database '$DATABASE' yet")

            val ref = GlueLakeTableResolver(glue, DATABASE).ref(table!!)

            assertTrue(
                ref.startsWith("iceberg_scan('s3://saramquant-bucket/warehouse/"),
                "unexpected ref for table '$table': $ref",
            )
            assertTrue(ref.endsWith("')"), ref)
        }
    }

    // 자격증명/권한 오류는 그대로 실패시키고, 데이터베이스 미생성만 skip 대상으로 둔다.
    private fun firstIcebergTable(glue: GlueClient): String? = try {
        glue.getTables(GetTablesRequest.builder().databaseName(DATABASE).build())
            .tableList()
            .firstOrNull { it.parameters()?.get("metadata_location")?.isNotBlank() == true }
            ?.name()
    } catch (e: EntityNotFoundException) {
        null
    }

    private fun realGlueClient(): GlueClient = GlueClient.builder()
        .region(Region.of(REGION))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    System.getenv("SARAMQUANT_IAM_KEY_ACCESS"),
                    System.getenv("SARAMQUANT_IAM_KEY_SECRET"),
                ),
            ),
        )
        .build()

    private fun stubGlue(metadataLocation: String?, calls: AtomicInteger = AtomicInteger()): GlueClient =
        object : GlueClient {
            override fun serviceName(): String = "glue-stub"

            override fun close() = Unit

            override fun getTable(request: GetTableRequest): GetTableResponse {
                calls.incrementAndGet()
                val parameters = metadataLocation?.let { mapOf("metadata_location" to it) } ?: emptyMap()
                return GetTableResponse.builder()
                    .table(Table.builder().name(request.name()).parameters(parameters).build())
                    .build()
            }
        }

    companion object {
        private const val DATABASE = "saramquant"
        private const val REGION = "ap-northeast-2"
    }
}
