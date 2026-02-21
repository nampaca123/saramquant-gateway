package me.saramquantgateway.infra.security.config

import me.saramquantgateway.infra.security.filter.JwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
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
    @param:Value("\${app.frontend-redirect-url}") private val frontendUrl: String,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers("/login/oauth2/code/**").permitAll()
                    .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/risk-badges/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/stocks/*/simulation").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    private fun corsSource(): CorsConfigurationSource {
        val origin = frontendUrl.substringBeforeLast("/")
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf(origin)
            allowedMethods = listOf(
                HttpMethod.GET.name(), HttpMethod.POST.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name(),
            )
            allowedHeaders = listOf("Content-Type")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
