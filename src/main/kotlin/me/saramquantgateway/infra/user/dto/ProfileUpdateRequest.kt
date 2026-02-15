package me.saramquantgateway.infra.user.dto

import me.saramquantgateway.domain.enum.Gender
import me.saramquantgateway.domain.enum.InvestmentExperience
import me.saramquantgateway.domain.enum.Market

data class ProfileUpdateRequest(
    val nickname: String? = null,
    val birthYear: Int? = null,
    val gender: Gender? = null,
    val investmentExperience: InvestmentExperience? = null,
    val preferredMarkets: Set<Market>? = null,
)
