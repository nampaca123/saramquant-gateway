package me.saramquantgateway.infra.user.dto

import me.saramquantgateway.domain.entity.User
import me.saramquantgateway.domain.enum.OAuthProvider
import java.util.UUID

data class UserResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val provider: OAuthProvider,
    val profile: ProfileResponse?,
) {
    companion object {
        fun from(user: User, profile: ProfileResponse?) = UserResponse(
            id = user.id,
            email = user.email,
            name = user.name,
            provider = user.provider,
            profile = profile,
        )
    }
}
