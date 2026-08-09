package me.saramquantgateway.infra.storage.s3

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.model.S3Object
import java.time.Instant

data class KvEntry(val key: String, val lastModified: Instant)

// 논리 키에 props.appPrefix를 붙여 S3를 JSON KV 스토어로 사용한다.
@Component
class S3KvStore(
    private val s3: S3Client,
    private val props: S3StorageProperties,
    private val objectMapper: ObjectMapper,
) {

    fun <T : Any> get(key: String, type: Class<T>): T? =
        try {
            val bytes = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(props.bucket).key(fullKey(key)).build(),
            ).asByteArray()
            objectMapper.readValue(bytes, type)
        } catch (e: NoSuchKeyException) {
            null
        }

    fun <T : Any> put(key: String, value: T) {
        s3.putObject(putRequest(key).build(), RequestBody.fromBytes(objectMapper.writeValueAsBytes(value)))
    }

    fun <T : Any> putIfAbsent(key: String, value: T): Boolean =
        try {
            s3.putObject(
                putRequest(key).ifNoneMatch("*").build(),
                RequestBody.fromBytes(objectMapper.writeValueAsBytes(value)),
            )
            true
        } catch (e: S3Exception) {
            if (e.statusCode() == 412 || e.statusCode() == 409) false else throw e
        }

    fun delete(key: String) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket).key(fullKey(key)).build())
    }

    fun listKeys(prefix: String): List<String> = listObjects(prefix).map { logicalKey(it.key()) }

    fun listEntries(prefix: String): List<KvEntry> =
        listObjects(prefix).map { KvEntry(logicalKey(it.key()), it.lastModified()) }

    private fun listObjects(prefix: String): List<S3Object> {
        val objects = mutableListOf<S3Object>()
        var token: String? = null
        while (true) {
            val response = s3.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(props.bucket)
                    .prefix(fullKey(prefix))
                    .continuationToken(token)
                    .build(),
            )
            objects += response.contents()
            if (response.isTruncated != true) break
            token = response.nextContinuationToken()
        }
        return objects
    }

    private fun putRequest(key: String): PutObjectRequest.Builder =
        PutObjectRequest.builder().bucket(props.bucket).key(fullKey(key)).contentType("application/json")

    private fun fullKey(key: String): String = props.appPrefix + key

    private fun logicalKey(key: String): String = key.removePrefix(props.appPrefix)
}
