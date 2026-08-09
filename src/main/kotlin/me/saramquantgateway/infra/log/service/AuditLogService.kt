package me.saramquantgateway.infra.log.service

import me.saramquantgateway.domain.store.AuditLogStore
import me.saramquantgateway.infra.log.dto.AuditLogResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId

@Service
class AuditLogService(
    private val store: AuditLogStore,
) {

    fun getLogs(
        server: String?,
        action: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        pageable: Pageable,
    ): Page<AuditLogResponse> {
        val zone = ZoneId.of("Asia/Seoul")
        val from = (startDate ?: LocalDate.of(2020, 1, 1)).atStartOfDay(zone).toInstant()
        val to = (endDate?.plusDays(1) ?: LocalDate.of(2099, 1, 1)).atStartOfDay(zone).toInstant()

        val result = store.findFiltered(
            server, action, from, to, pageable.pageNumber, pageable.pageSize,
        )
        return PageImpl(result.content.map { AuditLogResponse.from(it) }, pageable, result.totalElements)
    }
}
