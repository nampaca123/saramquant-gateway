package me.saramquantgateway.infra.user.controller

import me.saramquantgateway.infra.security.CookieUtil
import me.saramquantgateway.infra.storage.service.ProfileImageService
import me.saramquantgateway.infra.user.dto.ProfileResponse
import me.saramquantgateway.infra.user.dto.ProfileUpdateRequest
import me.saramquantgateway.infra.user.dto.UserResponse
import me.saramquantgateway.infra.auth.service.AuthService
import me.saramquantgateway.infra.user.service.ProfileService
import me.saramquantgateway.infra.user.service.UserService
import me.saramquantgateway.feature.systememail.service.SystemEmailService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService,
    private val profileService: ProfileService,
    private val profileImageService: ProfileImageService,
    private val authService: AuthService,
    private val cookieUtil: CookieUtil,
    private val systemEmailService: SystemEmailService,
) {

    @GetMapping("/me")
    fun me(principal: Principal): ResponseEntity<UserResponse> {
        val userId = UUID.fromString(principal.name)
        val user = userService.findById(userId)
            ?: return ResponseEntity.notFound().build()
        if (!user.isActive) return ResponseEntity.status(403).build()
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

    @PostMapping("/profile/image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadProfileImage(
        principal: Principal,
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<Map<String, String>> {
        val userId = UUID.fromString(principal.name)
        val contentType = file.contentType ?: "image/jpeg"
        val url = profileImageService.uploadFromBytes(userId, file.bytes, contentType)
        profileService.updateImageUrl(userId, url)
        return ResponseEntity.ok(mapOf("profileImageUrl" to url))
    }

    @DeleteMapping("/profile/image")
    fun deleteProfileImage(principal: Principal): ResponseEntity<Void> {
        val userId = UUID.fromString(principal.name)
        profileImageService.delete(userId)
        profileService.clearImageUrl(userId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/me")
    fun deactivateAccount(principal: Principal, response: HttpServletResponse): ResponseEntity<Void> {
        val userId = UUID.fromString(principal.name)
        val user = userService.findById(userId) ?: return ResponseEntity.notFound().build()
        authService.logoutAll(userId)
        userService.deactivateUser(userId)
        systemEmailService.sendDeactivationEmail(user)
        cookieUtil.clearAll(response)
        return ResponseEntity.noContent().build()
    }
}
