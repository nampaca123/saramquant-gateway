package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.domain.store.UserStore
import me.saramquantgateway.infra.user.dto.ProfileResponse
import me.saramquantgateway.infra.user.dto.ProfileUpdateRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ProfileService(private val userStore: UserStore) {

    fun getByUserId(userId: UUID): ProfileResponse? =
        userStore.findById(userId)?.let { ProfileResponse.from(it) }

    fun update(userId: UUID, req: ProfileUpdateRequest): ProfileResponse {
        val user = userStore.findById(userId)
            ?: throw IllegalArgumentException("Profile not found")

        req.nickname?.let { user.nickname = it }
        req.birthYear?.let { user.birthYear = it }
        req.gender?.let { user.gender = it }
        req.investmentExperience?.let { user.investmentExperience = it }
        req.preferredMarkets?.let {
            user.preferredMarkets.clear()
            user.preferredMarkets.addAll(it)
        }

        persist(user)
        return ProfileResponse.from(user)
    }

    fun updateImageUrl(userId: UUID, url: String) {
        userStore.findById(userId)?.let {
            it.profileImageKey = url
            persist(it)
        }
    }

    fun clearImageUrl(userId: UUID) {
        userStore.findById(userId)?.let {
            it.profileImageKey = null
            persist(it)
        }
    }

    private fun persist(user: UserDoc) {
        user.updatedAt = Instant.now()
        userStore.save(user)
    }
}
