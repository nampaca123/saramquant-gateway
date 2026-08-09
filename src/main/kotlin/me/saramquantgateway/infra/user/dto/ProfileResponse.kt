package me.saramquantgateway.infra.user.dto

import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.domain.enum.user.Gender
import me.saramquantgateway.domain.enum.user.InvestmentExperience
import me.saramquantgateway.domain.enum.stock.Market

data class ProfileResponse(
    val nickname: String?,
    val birthYear: Int?,
    val gender: Gender?,
    val profileImageUrl: String?,
    val investmentExperience: InvestmentExperience,
    val preferredMarkets: Set<Market>,
) {
    companion object {
        fun from(p: UserDoc) = ProfileResponse(
            nickname = p.nickname,
            birthYear = p.birthYear,
            gender = p.gender,
            profileImageUrl = p.profileImageKey,
            investmentExperience = p.investmentExperience,
            preferredMarkets = p.preferredMarkets,
        )
    }
}
