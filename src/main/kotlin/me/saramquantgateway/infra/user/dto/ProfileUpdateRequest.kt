package me.saramquantgateway.infra.user.dto

import me.saramquantgateway.domain.enum.user.Gender
import me.saramquantgateway.domain.enum.user.InvestmentExperience
import me.saramquantgateway.domain.enum.stock.Market

data class ProfileUpdateRequest(
    val nickname: String? = null,
    val birthYear: Int? = null,
    val gender: Gender? = null,
    val investmentExperience: InvestmentExperience? = null,
    val preferredMarkets: Set<Market>? = null,
)
