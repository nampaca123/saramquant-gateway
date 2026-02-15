package me.saramquantgateway.domain.repository.user

import me.saramquantgateway.domain.entity.user.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {

    fun findByUserId(userId: UUID): UserProfile?
}
