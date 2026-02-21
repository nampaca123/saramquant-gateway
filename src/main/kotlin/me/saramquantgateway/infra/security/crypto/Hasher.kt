package me.saramquantgateway.infra.security.crypto

import org.springframework.stereotype.Component
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class Hasher(props: CryptoProperties) {

    private val hmacKey: ByteArray =
        deriveKey(props.hashSecret.toByteArray(), "hmac-key".toByteArray())

    fun hash(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun deriveKey(master: ByteArray, context: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(master, "HmacSHA256"))
            return mac.doFinal(context)
        }
    }
}
