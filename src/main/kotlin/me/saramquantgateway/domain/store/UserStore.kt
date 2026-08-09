package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.EmailPointerDoc
import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.infra.security.crypto.AesEncryptor
import me.saramquantgateway.infra.storage.s3.S3KvStore
import org.springframework.stereotype.Component
import java.util.UUID

// 이메일 포인터(예약) → 유저 문서 순서로 쓰고, PII는 직렬화 직전에 암호화한다.
@Component
class UserStore(
    private val kv: S3KvStore,
    private val aes: AesEncryptor,
) {

    fun findById(userId: UUID): UserDoc? =
        kv.get(userKey(userId), UserDoc::class.java)?.let { decrypt(it) }

    fun findByEmailHash(emailHash: String): UserDoc? =
        kv.get(pointerKey(emailHash), EmailPointerDoc::class.java)?.let { findById(it.userId) }

    fun create(doc: UserDoc): Boolean {
        if (!kv.putIfAbsent(pointerKey(doc.emailHash), EmailPointerDoc(doc.id))) {
            // 유저 문서가 없는 고아 포인터는 예약을 덮어써서 회수한다.
            if (!isDanglingPointer(doc.emailHash)) return false
            kv.put(pointerKey(doc.emailHash), EmailPointerDoc(doc.id))
        }
        try {
            kv.put(userKey(doc.id), encrypt(doc))
        } catch (e: Exception) {
            runCatching { kv.delete(pointerKey(doc.emailHash)) }
            throw e
        }
        return true
    }

    fun save(doc: UserDoc) {
        kv.put(userKey(doc.id), encrypt(doc))
    }

    private fun isDanglingPointer(emailHash: String): Boolean {
        val pointer = kv.get(pointerKey(emailHash), EmailPointerDoc::class.java) ?: return true
        return kv.get(userKey(pointer.userId), UserDoc::class.java) == null
    }

    private fun encrypt(doc: UserDoc): UserDoc = doc.copy(
        email = aes.encrypt(doc.email),
        name = aes.encrypt(doc.name),
        providerId = aes.encrypt(doc.providerId),
        nickname = doc.nickname?.let { aes.encrypt(it) },
    )

    private fun decrypt(doc: UserDoc): UserDoc = doc.copy(
        email = aes.decrypt(doc.email),
        name = aes.decrypt(doc.name),
        providerId = aes.decrypt(doc.providerId),
        nickname = doc.nickname?.let { aes.decrypt(it) },
    )

    private fun userKey(userId: UUID) = "users/$userId.json"

    private fun pointerKey(emailHash: String) = "users/by-email/$emailHash.json"
}
