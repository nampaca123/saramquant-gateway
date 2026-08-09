package me.saramquantgateway.infra.duckdb

import org.duckdb.DuckDBConnection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.CopyOnWriteArrayList

// 스레드마다 root 커넥션의 duplicate()를 재사용해 동시 쿼리가 직렬화되지 않게 한다.
// 가상 스레드를 켜면 요청마다 커넥션이 새로 생겨 누수가 되므로 Tomcat 고정 풀 전제를 유지한다.
class DuckDbQueryExecutor(
    private val rootConnection: DuckDBConnection,
    private val onStaleLakeMetadata: () -> Unit = {},
) : AutoCloseable {

    private val duplicates = CopyOnWriteArrayList<DuckDBConnection>()

    private val threadConnection = ThreadLocal.withInitial {
        (rootConnection.duplicate() as DuckDBConnection).also { duplicates.add(it) }
    }

    fun <T> query(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T> =
        try {
            execute(sql, params, mapper)
        } catch (e: SQLException) {
            if (isStaleLakeMetadata(e)) onStaleLakeMetadata()
            throw e
        }

    private fun <T> execute(sql: String, params: List<Any?>, mapper: (ResultSet) -> T): List<T> =
        currentConnection().prepareStatement(sql).use { statement ->
            params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
            statement.executeQuery().use { rs ->
                val rows = mutableListOf<T>()
                while (rs.next()) rows.add(mapper(rs))
                rows
            }
        }

    // VACUUM 직후 캐시된 metadata_location이 가리키는 파일이 사라진 경우를 잡아낸다.
    private fun isStaleLakeMetadata(e: SQLException): Boolean {
        val message = generateSequence(e as Throwable) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        return STALE_METADATA_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    internal fun currentConnection(): DuckDBConnection = threadConnection.get()

    override fun close() {
        duplicates.forEach { runCatching { it.close() } }
        duplicates.clear()
        rootConnection.close()
    }

    private companion object {
        val STALE_METADATA_MARKERS = listOf("NoSuchKey", "404", "HTTP GET error", "No such file")
    }
}
