package me.saramquantgateway.infra.systememail.dto

import jakarta.validation.constraints.Email

data class SendVerificationRequest(
    @field:Email val email: String,
)
