package me.saramquantgateway.infra.jwt.lib

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import me.saramquantgateway.domain.enum.OAuthProvider
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID

@Component
class JwtProvider(private val props: JwtProperties) {

    fun generateAccessToken(userId: UUID, email: String, provider: OAuthProvider): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("provider", provider.name)
            .claim("type", "access")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + props.accessTokenTtl * 1000))
            .signWith(props.privateKey)
            .compact()

    fun generateRefreshToken(userId: UUID): String =
        Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + props.refreshTokenTtl * 1000))
            .signWith(props.privateKey)
            .compact()

    fun validateToken(token: String): Claims? =
        try {
            Jwts.parser()
                .verifyWith(props.publicKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (_: JwtException) {
            null
        }

    fun extractUserId(token: String): UUID? =
        validateToken(token)?.subject?.let { UUID.fromString(it) }
}
