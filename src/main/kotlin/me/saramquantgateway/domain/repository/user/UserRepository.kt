package me.saramquantgateway.domain.repository.user

import me.saramquantgateway.domain.entity.user.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmail(email: String): User?
}
