package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.AuditLogDoc
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.storage.s3.S3KvStore
import me.saramquantgateway.infra.storage.s3.S3StorageProperties
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

data class VisitorStat(val visitDate: LocalDate, val hour: Int, val ipMasked: String, val path: String?)

// audit-log/dt=YYYY-MM-DD/{epochMillis}-{shortId}.json — 쓰기는 S3 append, 조회는 DuckDB read_json.
@Component
class AuditLogStore(
    private val kv: S3KvStore,
    private val duckDb: DuckDbQueryExecutor,
    private val props: S3StorageProperties,
) {

    fun append(doc: AuditLogDoc) {
        val at = doc.createdAt.truncatedTo(ChronoUnit.MILLIS)
        val day = LocalDate.ofInstant(at, ZoneOffset.UTC)
        val suffix = UUID.randomUUID().toString().take(8)
        kv.put("$PREFIX/dt=$day/${at.toEpochMilli()}-$suffix.json", doc.copy(createdAt = at))
    }

    fun findFiltered(
        server: String?,
        action: String?,
        from: Instant,
        to: Instant,
        page: Int,
        size: Int,
    ): PageResult<AuditLogDoc> {
        val days = existingDays(from, to)
        if (days.isEmpty()) return PageResult(emptyList(), 0, false)

        val params = mutableListOf<Any?>(timestamp(from), timestamp(to))
        val where = buildString {
            append("ts >= CAST(? AS TIMESTAMP) AND ts < CAST(? AS TIMESTAMP)")
            if (server != null) { append(" AND server = ?"); params += server }
            if (action != null) { append(" AND action = ?"); params += action }
        }

        val total = duckDb.query("SELECT count(*) AS c FROM ${source(days)} WHERE $where", params) {
            it.getLong("c")
        }.single()
        val offset = page.toLong() * size
        val rows = duckDb.query(
            "SELECT $COLUMNS FROM ${source(days)} WHERE $where ORDER BY ts DESC LIMIT $size OFFSET $offset",
            params,
            ::toDoc,
        )
        return PageResult(rows, total, offset + rows.size < total)
    }

    fun countVisitors(from: Instant, to: Instant): List<VisitorStat> {
        val days = existingDays(from, to)
        if (days.isEmpty()) return emptyList()

        return duckDb.query(
            """
            SELECT DISTINCT CAST(ts + INTERVAL '9 hours' AS DATE) AS visit_date,
                   CAST(EXTRACT(HOUR FROM ts + INTERVAL '9 hours') AS INTEGER) AS hour,
                   ipMasked AS ip_masked, path
            FROM ${source(days)}
            WHERE ts >= CAST(? AS TIMESTAMP) AND ts < CAST(? AS TIMESTAMP) AND ipMasked IS NOT NULL
            """.trimIndent(),
            listOf(timestamp(from), timestamp(to)),
        ) {
            VisitorStat(
                visitDate = it.getDate("visit_date").toLocalDate(),
                hour = it.getInt("hour"),
                ipMasked = it.getString("ip_masked"),
                path = it.getString("path"),
            )
        }
    }

    // dt= 프리픽스가 없는 날의 glob은 DuckDB에서 에러가 나므로 실제 존재하는 날만 넘긴다.
    private fun existingDays(from: Instant, to: Instant): List<LocalDate> {
        val fromDay = LocalDate.ofInstant(from, ZoneOffset.UTC)
        val toDay = LocalDate.ofInstant(to, ZoneOffset.UTC)
        return kv.listKeys("$PREFIX/")
            .mapNotNull { DAY_PATTERN.find(it)?.groupValues?.get(1) }
            .distinct()
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isBefore(fromDay) && !it.isAfter(toDay) }
            .sorted()
    }

    private fun source(days: List<LocalDate>): String {
        val globs = days.joinToString(", ") { "'s3://${props.bucket}/${props.appPrefix}$PREFIX/dt=$it/*.json'" }
        return "(SELECT *, CAST(replace(replace(createdAt, 'T', ' '), 'Z', '') AS TIMESTAMP) AS ts " +
            "FROM read_json([$globs], columns={$SCHEMA}, format='auto'))"
    }

    private fun timestamp(instant: Instant): String = TIMESTAMP_FMT.format(instant)

    private fun toDoc(rs: ResultSet) = AuditLogDoc(
        id = UUID.fromString(rs.getString("id")),
        server = rs.getString("server"),
        action = rs.getString("action"),
        method = rs.getString("method"),
        path = rs.getString("path"),
        ipMasked = rs.getString("ip_masked"),
        userId = rs.getString("user_id")?.let(UUID::fromString),
        statusCode = rs.getInt("status_code").takeIf { !rs.wasNull() },
        durationMs = rs.getLong("duration_ms").takeIf { !rs.wasNull() },
        metadata = rs.getString("metadata"),
        createdAt = Instant.ofEpochMilli(rs.getLong("created_ms")),
    )

    companion object {
        private const val PREFIX = "audit-log"
        private val DAY_PATTERN = Regex("""dt=(\d{4}-\d{2}-\d{2})/""")
        private val TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC)

        private const val SCHEMA =
            "id: 'VARCHAR', server: 'VARCHAR', action: 'VARCHAR', method: 'VARCHAR', path: 'VARCHAR', " +
                "ipMasked: 'VARCHAR', userId: 'VARCHAR', statusCode: 'INTEGER', durationMs: 'BIGINT', " +
                "metadata: 'VARCHAR', createdAt: 'VARCHAR'"

        private const val COLUMNS =
            "id, server, action, method, path, ipMasked AS ip_masked, userId AS user_id, " +
                "statusCode AS status_code, durationMs AS duration_ms, metadata, epoch_ms(ts) AS created_ms"
    }
}
