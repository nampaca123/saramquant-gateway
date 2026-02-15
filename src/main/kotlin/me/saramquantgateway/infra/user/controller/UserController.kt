package me.saramquantgateway.infra.user.controller

import me.saramquantgateway.infra.security.CookieUtil
import me.saramquantgateway.infra.user.dto.ProfileResponse
import me.saramquantgateway.infra.user.dto.ProfileUpdateRequest
import me.saramquantgateway.infra.user.dto.UserResponse
import me.saramquantgateway.infra.user.service.AuthService
import me.saramquantgateway.infra.user.service.ProfileService
import me.saramquantgateway.infra.user.service.UserService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService,
    private val profileService: ProfileService,
    private val authService: AuthService,
    private val cookieUtil: CookieUtil,
) {

    @GetMapping("/me")
    fun me(principal: Principal): ResponseEntity<UserResponse> {
        val userId = UUID.fromString(principal.name)
        val user = userService.findById(userId)
            ?: return ResponseEntity.notFound().build()
        val profile = profileService.getByUserId(userId)
        return ResponseEntity.ok(UserResponse.from(user, profile))
    }

    @PatchMapping("/profile")
    fun updateProfile(
        principal: Principal,
        @RequestBody req: ProfileUpdateRequest,
    ): ResponseEntity<ProfileResponse> {
        val userId = UUID.fromString(principal.name)
        return ResponseEntity.ok(profileService.update(userId, req))
    }

    @DeleteMapping("/me")
    fun deleteAccount(principal: Principal, response: HttpServletResponse): ResponseEntity<Void> {
        val userId = UUID.fromString(principal.name)
        authService.logoutAll(userId)
        userService.deleteUser(userId)
        cookieUtil.clearAll(response)
        return ResponseEntity.noContent().build()
    }
}
