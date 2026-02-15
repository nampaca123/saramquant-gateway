package me.saramquantgateway.infra.user.dto

import me.saramquantgateway.domain.entity.user.User
import me.saramquantgateway.domain.enum.auth.OAuthProvider
import me.saramquantgateway.domain.enum.user.UserRole
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val provider: OAuthProvider,
    val role: UserRole,
    val profile: ProfileResponse?,
) {
    companion object {
        fun from(user: User, profile: ProfileResponse?) = UserResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            provider = user.provider,
            role = user.role,
            profile = profile,
        )
    }
}
