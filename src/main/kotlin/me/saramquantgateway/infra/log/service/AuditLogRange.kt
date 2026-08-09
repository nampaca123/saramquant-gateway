package me.saramquantgateway.infra.log.service

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 감사 로그 조회 구간 — 기본 최근 90일, 최대 92일로 묶어 S3 조회량을 유계로 만든다.
object AuditLogRange {

    private const val DEFAULT_DAYS = 90L
    private const val MAX_DAYS = 92L
    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

    fun resolve(startDate: LocalDate?, endDate: LocalDate?): Pair<Instant, Instant> {
        val end = endDate ?: LocalDate.now(ZONE)
        val start = startDate ?: end.minusDays(DEFAULT_DAYS - 1)
        val span = ChronoUnit.DAYS.between(start, end)
        if (span < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate")
        }
        if (span >= MAX_DAYS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Date range must not exceed $MAX_DAYS days")
        }
        return start.atStartOfDay(ZONE).toInstant() to end.plusDays(1).atStartOfDay(ZONE).toInstant()
    }
}
