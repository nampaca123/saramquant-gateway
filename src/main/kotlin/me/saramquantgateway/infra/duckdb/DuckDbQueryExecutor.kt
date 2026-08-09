package me.saramquantgateway.infra.duckdb

import org.duckdb.DuckDBConnection
import java.sql.ResultSet
import java.util.concurrent.CopyOnWriteArrayList

// 스레드마다 root 커넥션의 duplicate()를 재사용해 동시 쿼리가 직렬화되지 않게 한다.
class DuckDbQueryExecutor(private val rootConnection: DuckDBConnection) : AutoCloseable {

    private val duplicates = CopyOnWriteArrayList<DuckDBConnection>()

    private val threadConnection = ThreadLocal.withInitial {
        (rootConnection.duplicate() as DuckDBConnection).also { duplicates.add(it) }
    }

    fun <T> query(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T> =
        currentConnection().prepareStatement(sql).use { statement ->
            params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
            statement.executeQuery().use { rs ->
                val rows = mutableListOf<T>()
                while (rs.next()) rows.add(mapper(rs))
                rows
            }
        }

    internal fun currentConnection(): DuckDBConnection = threadConnection.get()

    override fun close() {
        duplicates.forEach { runCatching { it.close() } }
        duplicates.clear()
        rootConnection.close()
    }
}
