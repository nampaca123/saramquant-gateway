package me.saramquantgateway.infra.duckdb

import org.duckdb.DuckDBConnection
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.glue.GlueClient
import software.amazon.awssdk.services.glue.model.GetTablesRequest
import java.sql.DriverManager
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DuckDbQueryExecutorTest {

    @Test
    fun `query maps every row in order`() {
        val rows = executor.query("SELECT id, name FROM t WHERE name IS NOT NULL ORDER BY id") { rs ->
            rs.getInt("id") to rs.getString("name")
        }

        assertEquals(listOf(1 to "a", 2 to "b"), rows)
    }

    @Test
    fun `query returns empty list when nothing matches`() {
        val rows = executor.query("SELECT id FROM t WHERE id = ?", listOf(999)) { it.getInt("id") }

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `query binds parameters instead of interpolating them`() {
        val rows = executor.query(
            "SELECT id FROM t WHERE name = ? AND d = ?",
            listOf("a", LocalDate.of(2026, 1, 1)),
        ) { it.getInt("id") }

        assertEquals(listOf(1), rows)
    }

    @Test
    fun `query treats an injection payload as a literal value`() {
        val rows = executor.query(
            "SELECT id FROM t WHERE name = ?",
            listOf("a'; DROP TABLE t; --"),
        ) { it.getInt("id") }

        assertTrue(rows.isEmpty())
        assertEquals(listOf(3L), executor.query("SELECT count(*) AS c FROM t") { it.getLong("c") })
    }

    @Test
    fun `query binds null parameters`() {
        val rows = executor.query(
            "SELECT id FROM t WHERE (? IS NULL AND name IS NULL) OR name = ?",
            listOf(null, "b"),
        ) { it.getInt("id") }

        assertEquals(listOf(2, 3), rows.sorted())
    }

    @Test
    fun `parallel queries run on a duplicated connection per thread`() {
        val threads = 4
        val pool = Executors.newFixedThreadPool(threads)
        val barrier = CyclicBarrier(threads)
        val connectionIds = ConcurrentHashMap.newKeySet<Int>()

        val results = (1..threads).map { worker ->
            pool.submit(
                Callable {
                    barrier.await(30, TimeUnit.SECONDS)
                    connectionIds.add(System.identityHashCode(executor.currentConnection()))
                    repeat(25) {
                        val ids = executor.query("SELECT id FROM t WHERE id <= ? ORDER BY id", listOf(worker)) {
                            it.getInt("id")
                        }
                        assertEquals((1..minOf(worker, 3)).toList(), ids)
                    }
                    executor.query("SELECT count(*) AS c FROM t") { it.getLong("c") }.single()
                },
            )
        }.map { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(List(threads) { 3L }, results)
        assertEquals(threads, connectionIds.size)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
    fun `initialized connection has httpfs iceberg and aws loaded plus the s3 secret`() {
        DuckDbConfig.openInitializedConnection(REGION).use { initialized ->
            DuckDbQueryExecutor(initialized).use { initializedExecutor ->
                val loaded = initializedExecutor.query(
                    "SELECT extension_name FROM duckdb_extensions() WHERE loaded AND extension_name IN ('httpfs', 'iceberg', 'aws')",
                ) { it.getString(1) }
                val secrets = initializedExecutor.query("SELECT name FROM duckdb_secrets()") { it.getString(1) }

                assertEquals(listOf("aws", "httpfs", "iceberg"), loaded.sorted())
                assertTrue(secrets.contains("s3sec"), "secrets=$secrets")
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
    fun `initialized connection scans a real iceberg table through the resolver`() {
        GlueClient.builder().region(Region.of(REGION)).build().use { glue ->
            val table = glue.getTables(GetTablesRequest.builder().databaseName(DATABASE).build())
                .tableList()
                .firstOrNull { it.parameters()?.get("metadata_location")?.isNotBlank() == true }
                ?.name()
            assumeTrue(table != null, "no iceberg table in glue database '$DATABASE' yet")

            DuckDbConfig.openInitializedConnection(REGION).use { initialized ->
                DuckDbQueryExecutor(initialized).use { initializedExecutor ->
                    val ref = GlueLakeTableResolver(glue, DATABASE).ref(table!!)

                    val count = initializedExecutor.query("SELECT count(*) AS c FROM $ref") {
                        it.getLong("c")
                    }.single()

                    assertTrue(count >= 0, "count=$count")
                }
            }
        }
    }

    companion object {
        private const val REGION = "ap-northeast-2"
        private const val DATABASE = "saramquant"

        private lateinit var connection: DuckDBConnection
        private lateinit var executor: DuckDbQueryExecutor

        @BeforeAll
        @JvmStatic
        fun setUp() {
            connection = DriverManager.getConnection("jdbc:duckdb:") as DuckDBConnection
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE t (id INTEGER, name VARCHAR, d DATE)")
                statement.execute(
                    "INSERT INTO t VALUES (1, 'a', DATE '2026-01-01'), (2, 'b', DATE '2026-01-02'), (3, NULL, NULL)",
                )
            }
            executor = DuckDbQueryExecutor(connection)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            executor.close()
        }
    }
}
