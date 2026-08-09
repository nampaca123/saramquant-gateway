package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.domain.store.UserStore
import me.saramquantgateway.infra.storage.service.ProfileImageService
import me.saramquantgateway.infra.user.dto.ProfileResponse
import me.saramquantgateway.infra.user.dto.ProfileUpdateRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class ProfileService(
    private val userStore: UserStore,
    private val profileImageService: ProfileImageService,
) {

    fun getByUserId(userId: UUID): ProfileResponse? =
        userStore.findById(userId)?.let { toResponse(it) }

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
        return toResponse(user)
    }

    fun updateImageKey(userId: UUID, key: String) {
        userStore.findById(userId)?.let {
            it.profileImageKey = key
            persist(it)
        }
    }

    fun clearImageKey(userId: UUID) {
        userStore.findById(userId)?.let {
            it.profileImageKey = null
            persist(it)
        }
    }

    // 저장은 S3 키로, 응답은 presigned URL로 나간다.
    private fun toResponse(user: UserDoc): ProfileResponse =
        ProfileResponse.from(user, user.profileImageKey?.let { profileImageService.presignedUrl(it) })

    private fun persist(user: UserDoc) {
        user.updatedAt = Instant.now()
        userStore.save(user)
    }
}
