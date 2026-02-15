package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.repository.user.UserProfileRepository
import me.saramquantgateway.infra.user.dto.ProfileResponse
import me.saramquantgateway.infra.user.dto.ProfileUpdateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProfileService(private val profileRepo: UserProfileRepository) {

    fun getByUserId(userId: UUID): ProfileResponse? =
        profileRepo.findByUserId(userId)?.let { ProfileResponse.from(it) }

    @Transactional
    fun update(userId: UUID, req: ProfileUpdateRequest): ProfileResponse {
        val profile = profileRepo.findByUserId(userId)
            ?: throw IllegalArgumentException("Profile not found")

        req.nickname?.let { profile.nickname = it }
        req.birthYear?.let { profile.birthYear = it }
        req.gender?.let { profile.gender = it }
        req.investmentExperience?.let { profile.investmentExperience = it }
        req.preferredMarkets?.let {
            profile.preferredMarkets.clear()
            profile.preferredMarkets.addAll(it)
        }

        return ProfileResponse.from(profileRepo.save(profile))
    }

    @Transactional
    fun updateImageUrl(userId: UUID, url: String) {
        profileRepo.findByUserId(userId)?.let {
            it.profileImageUrl = url
            profileRepo.save(it)
        }
    }
}
