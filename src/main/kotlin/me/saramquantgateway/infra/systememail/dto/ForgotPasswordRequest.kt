package me.saramquantgateway.infra.systememail.dto

import jakarta.validation.constraints.Email

data class ForgotPasswordRequest(
    @field:Email val email: String,
)
