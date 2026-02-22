package me.saramquantgateway.feature.home.controller

import me.saramquantgateway.feature.home.dto.HomeSummary
import me.saramquantgateway.feature.home.service.HomeService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/home")
class HomeController(private val service: HomeService) {

    @GetMapping("/summary")
    fun summary(): ResponseEntity<HomeSummary> {
        val userId = try {
            UUID.fromString(SecurityContextHolder.getContext().authentication?.name)
        } catch (_: Exception) {
            null
        }
        return ResponseEntity.ok(service.summary(userId))
    }
}
