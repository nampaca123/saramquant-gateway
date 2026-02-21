package me.saramquantgateway.infra.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class ManualSignupRequest(
    @field:Email val email: String,
    @field:Size(min = 8, max = 100) val password: String,
    @field:Size(min = 1, max = 100) val name: String,
)
