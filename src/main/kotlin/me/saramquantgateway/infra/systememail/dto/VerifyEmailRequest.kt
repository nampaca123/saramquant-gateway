package me.saramquantgateway.infra.systememail.dto

import jakarta.validation.constraints.Email

data class VerifyEmailRequest(
    @field:Email val email: String,
    val code: String,
    val purpose: String,
)
