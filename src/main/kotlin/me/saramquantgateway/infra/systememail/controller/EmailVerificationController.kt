package me.saramquantgateway.infra.systememail.controller

import jakarta.validation.Valid
import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.infra.systememail.dto.*
import me.saramquantgateway.infra.systememail.enum.VerificationPurpose
import me.saramquantgateway.infra.systememail.service.EmailVerificationService
import me.saramquantgateway.infra.systememail.service.EmailVerificationService.*
import me.saramquantgateway.infra.user.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class EmailVerificationController(
    private val verificationService: EmailVerificationService,
    private val userService: UserService,
) {

    @PostMapping("/api/auth/send-verification")
    fun sendVerification(
        @Valid @RequestBody req: SendVerificationRequest,
    ): ResponseEntity<Map<String, String>> {
        val existing = userService.findByEmail(req.email)
        if (existing != null) {
            return if (!existing.isActive) {
                error(409, "ACCOUNT_DEACTIVATED", "Account deactivated. Log in to reactivate.")
            } else {
                error(409, "EMAIL_EXISTS", "Email already in use.")
            }
        }

        return try {
            verificationService.sendCode(req.email, VerificationPurpose.SIGNUP)
            ResponseEntity.ok().build()
        } catch (_: RateLimitedException) {
            error(429, "RATE_LIMITED", "Please wait before requesting another code.")
        }
    }

    @PostMapping("/api/auth/forgot-password")
    fun forgotPassword(
        @Valid @RequestBody req: ForgotPasswordRequest,
    ): ResponseEntity<Map<String, String>> {
        val user = userService.findActiveByEmail(req.email)

        if (user == null || user.provider != AuthProvider.MANUAL || user.passwordHash == null) {
            return ResponseEntity.ok().build()
        }

        return try {
            verificationService.sendCodeForPasswordReset(req.email, user.name)
            ResponseEntity.ok().build()
        } catch (_: RateLimitedException) {
            error(429, "RATE_LIMITED", "Please wait before requesting another code.")
        }
    }

    @PostMapping("/api/auth/verify-email")
    fun verifyEmail(
        @Valid @RequestBody req: VerifyEmailRequest,
    ): ResponseEntity<Any> {
        if (!req.code.matches(Regex("^\\d{5}$"))) {
            return ResponseEntity.badRequest()
                .body(mapOf("code" to "INVALID_CODE_FORMAT", "message" to "Code must be 5 digits."))
        }

        val purpose = try {
            VerificationPurpose.valueOf(req.purpose)
        } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest()
                .body(mapOf("code" to "INVALID_PURPOSE", "message" to "Invalid purpose."))
        }

        return try {
            val verificationId = verificationService.verify(req.email, purpose, req.code)
            ResponseEntity.ok(VerifyEmailResponse(verificationId))
        } catch (_: InvalidCodeException) {
            ResponseEntity.badRequest()
                .body(mapOf("code" to "INVALID_CODE", "message" to "Invalid verification code."))
        } catch (_: CodeExpiredException) {
            ResponseEntity.status(410)
                .body(mapOf("code" to "CODE_EXPIRED", "message" to "Verification code has expired."))
        } catch (_: TooManyAttemptsException) {
            ResponseEntity.status(429)
                .body(mapOf("code" to "TOO_MANY_ATTEMPTS", "message" to "Too many incorrect attempts."))
        }
    }

    private fun error(status: Int, code: String, message: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).body(mapOf("code" to code, "message" to message))
}
