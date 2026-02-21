package me.saramquantgateway.infra.log.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_log")
class AuditLog(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    val server: String,

    @Column(nullable = false)
    val action: String,

    val method: String? = null,
    val path: String? = null,

    @Column(name = "ip_geolocation_id")
    var ipGeolocationId: UUID? = null,

    @Column(name = "user_id")
    val userId: UUID? = null,

    @Column(name = "status_code")
    val statusCode: Int? = null,

    @Column(name = "duration_ms")
    val durationMs: Long? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val metadata: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
