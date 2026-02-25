package me.saramquantgateway.infra.security.config

import me.saramquantgateway.infra.log.filter.AuditLogFilter
import me.saramquantgateway.infra.security.filter.GatewayAuthFilter
import me.saramquantgateway.infra.security.filter.JwtAuthenticationFilter
import me.saramquantgateway.infra.security.filter.RateLimitFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter,
    private val gatewayAuthFilter: GatewayAuthFilter,
    private val rateLimitFilter: RateLimitFilter,
    private val auditLogFilter: AuditLogFilter,
    @param:Value("\${app.cors.allowed-origin}") private val corsOrigin: String,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/login/oauth2/code/**").permitAll()
                    .requestMatchers("/api/auth/signup", "/api/auth/login").permitAll()
                    .requestMatchers("/api/auth/send-verification", "/api/auth/verify-email").permitAll()
                    .requestMatchers("/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                    .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/home/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/dashboard/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/stocks/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/stocks/*/simulation").permitAll()
                    .requestMatchers("/api/admin/**").authenticated()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(gatewayAuthFilter, RateLimitFilter::class.java)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(auditLogFilter, JwtAuthenticationFilter::class.java)
            .build()

    private fun corsSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf(corsOrigin)
            allowedMethods = listOf(
                HttpMethod.GET.name(), HttpMethod.POST.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
            )
            allowedHeaders = listOf("Content-Type", "X-Gateway-Auth-Key")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
