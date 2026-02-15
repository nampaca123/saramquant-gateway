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
    val privateKey: RSAPrivateKey = decodePrivateKey(privateKeyBase64)
    val publicKey: RSAPublicKey = decodePublicKey(publicKeyBase64)

    private fun decodePrivateKey(base64: String): RSAPrivateKey {
        val pem = String(Base64.getDecoder().decode(base64))
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))
        return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateKey
    }

    private fun decodePublicKey(base64: String): RSAPublicKey {
        val pem = String(Base64.getDecoder().decode(base64))
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val spec = X509EncodedKeySpec(Base64.getDecoder().decode(pem))
        return KeyFactory.getInstance("RSA").generatePublic(spec) as RSAPublicKey
    }
}
