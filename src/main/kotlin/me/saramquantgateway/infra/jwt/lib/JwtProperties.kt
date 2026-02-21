package me.saramquantgateway.infra.jwt.lib

import org.springframework.boot.context.properties.ConfigurationProperties
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@ConfigurationProperties(prefix = "jwt")
class JwtProperties(
    privateKeyBase64: String,
    publicKeyBase64: String,
    val accessTokenTtl: Long,
    val refreshTokenTtl: Long,
) {
    private val kf = KeyFactory.getInstance("RSA")

    val privateKey: RSAPrivateKey =
        kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64))) as RSAPrivateKey

    val publicKey: RSAPublicKey =
        kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))) as RSAPublicKey
}
