package me.saramquantgateway.infra.log.repository

import me.saramquantgateway.infra.log.entity.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:server IS NULL OR a.server = :server)
          AND (:action IS NULL OR a.action = :action)
          AND a.createdAt >= :from AND a.createdAt < :to
        ORDER BY a.createdAt DESC
    """)
    fun findFiltered(
        server: String?,
        action: String?,
        from: Instant,
        to: Instant,
        pageable: Pageable,
    ): Page<AuditLog>
}
