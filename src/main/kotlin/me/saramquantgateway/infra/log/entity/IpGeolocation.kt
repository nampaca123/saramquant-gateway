package me.saramquantgateway.infra.log.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "ip_geolocations")
class IpGeolocation(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "ip_hash", nullable = false, unique = true)
    val ipHash: String,

    @Column(name = "ip_masked", nullable = false)
    val ipMasked: String,

    val country: String? = null,

    @Column(name = "region_1")
    val region1: String? = null,

    @Column(name = "region_2")
    val region2: String? = null,

    @Column(name = "region_3")
    val region3: String? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,

    @Column(name = "network_provider")
    val networkProvider: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
