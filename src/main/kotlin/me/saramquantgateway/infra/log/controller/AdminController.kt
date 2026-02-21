package me.saramquantgateway.infra.log.controller

import me.saramquantgateway.infra.log.dto.AuditLogResponse
import me.saramquantgateway.infra.log.dto.VisitStatsResponse
import me.saramquantgateway.infra.log.service.AuditLogService
import me.saramquantgateway.infra.log.service.VisitStatsService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
class AdminController(
    private val auditLogService: AuditLogService,
    private val visitStatsService: VisitStatsService,
) {

    @GetMapping("/logs")
    fun getLogs(
        @RequestParam(required = false) server: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @PageableDefault(size = 50) pageable: Pageable,
    ): ResponseEntity<Page<AuditLogResponse>> =
        ResponseEntity.ok(auditLogService.getLogs(server, action, startDate, endDate, pageable))

    @GetMapping("/visitors")
    fun getVisitorStats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): ResponseEntity<VisitStatsResponse> =
        ResponseEntity.ok(visitStatsService.getStats(startDate, endDate))
}
