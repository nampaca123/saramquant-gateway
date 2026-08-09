package me.saramquantgateway.infra.duckdb

import me.saramquantgateway.infra.storage.s3.S3StorageProperties
import org.duckdb.DuckDBConnection
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.glue.GlueClient
import java.nio.file.Files
import java.nio.file.Paths
import java.sql.DriverManager

@Configuration
class DuckDbConfig(
    private val s3Properties: S3StorageProperties,
    @param:Value("\${app.lake.glue-database}") private val glueDatabase: String,
) {

    @Bean(destroyMethod = "close")
    fun duckDbQueryExecutor(lakeTableResolver: LakeTableResolver): DuckDbQueryExecutor =
        DuckDbQueryExecutor(openInitializedConnection(s3Properties.region), lakeTableResolver::invalidateAll)

    @Bean(destroyMethod = "close")
    fun glueClient(): GlueClient = GlueClient.builder()
        .region(Region.of(s3Properties.region))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build()

    @Bean
    fun lakeTableResolver(glueClient: GlueClient): LakeTableResolver =
        GlueLakeTableResolver(glueClient, glueDatabase)

    companion object {
        // 확장 디렉토리는 INSTALL 이전에 지정해야 캐시가 적용된다.
        fun openInitializedConnection(region: String): DuckDBConnection {
            val connection = DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection
            try {
                connection.createStatement().use { statement ->
                    initStatements(region).forEach { statement.execute(it) }
                }
            } catch (e: Exception) {
                runCatching { connection.close() }
                throw IllegalStateException("DuckDB initialization failed: ${e.message}", e)
            }
            return connection
        }

        // credential_chain은 aws 확장에 있어 암묵 autoload에 기대지 않고 명시적으로 설치·로드한다.
        val EXTENSIONS = listOf("httpfs", "iceberg", "aws")

        private fun initStatements(region: String): List<String> = buildList {
            System.getenv("DUCKDB_EXT_DIR")?.takeIf { it.isNotBlank() }?.let {
                add("SET extension_directory='${sqlPath(it)}'")
            }
            add("SET autoinstall_known_extensions=false")
            add("SET autoload_known_extensions=false")
            EXTENSIONS.forEach { add("INSTALL $it") }
            EXTENSIONS.forEach { add("LOAD $it") }
            add("CREATE OR REPLACE SECRET s3sec (TYPE s3, PROVIDER credential_chain, REGION '$region')")
            add("SET memory_limit='${memoryLimit()}'")
            add("SET temp_directory='${sqlPath(tempDirectory())}'")
        }

        private fun memoryLimit(): String =
            System.getenv("DUCKDB_MEMORY_LIMIT")?.takeIf { it.isNotBlank() } ?: "512MB"

        private fun tempDirectory(): String {
            val path = Paths.get(System.getProperty("java.io.tmpdir"), "duckdb")
            Files.createDirectories(path)
            return path.toString()
        }

        private fun sqlPath(path: String): String = path.replace('\\', '/').replace("'", "''")
    }
}
