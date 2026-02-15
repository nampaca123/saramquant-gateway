package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {

    fun findByUserId(userId: UUID): UserProfile?
}
