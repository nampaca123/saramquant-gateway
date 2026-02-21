package me.saramquantgateway.infra.log.repository

import me.saramquantgateway.infra.log.entity.IpGeolocation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IpGeolocationRepository : JpaRepository<IpGeolocation, UUID> {

    fun findByIpHash(ipHash: String): IpGeolocation?
}
