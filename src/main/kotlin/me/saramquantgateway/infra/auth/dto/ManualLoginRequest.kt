package me.saramquantgateway.infra.auth.dto

import jakarta.validation.constraints.Email

data class ManualLoginRequest(
    @field:Email val email: String,
    val password: String,
)
