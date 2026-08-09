package me.saramquantgateway.infra.storage.s3

import org.springframework.stereotype.Component
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.util.UUID

// 프로필 이미지 원본을 props.appPrefix 하위에 저장하고, 읽기는 1시간 presigned GET URL로만 노출한다.
@Component
class S3ProfileImageClient(
    private val s3: S3Client,
    private val presigner: S3Presigner,
    private val props: S3StorageProperties,
) {

    fun upload(userId: UUID, bytes: ByteArray, contentType: String): String {
        val key = objectKey(userId, extensionOf(contentType))
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(props.bucket).key(fullKey(key)).contentType(contentType).build(),
            RequestBody.fromBytes(bytes),
        )
        return key
    }

    fun delete(userId: UUID) {
        EXTENSIONS.forEach { ext ->
            s3.deleteObject(
                DeleteObjectRequest.builder().bucket(props.bucket).key(fullKey(objectKey(userId, ext))).build(),
            )
        }
    }

    fun presignedUrl(key: String): String =
        presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(URL_TTL)
                .getObjectRequest(GetObjectRequest.builder().bucket(props.bucket).key(fullKey(key)).build())
                .build(),
        ).url().toString()

    private fun objectKey(userId: UUID, ext: String): String = "profile-images/$userId.$ext"

    private fun extensionOf(contentType: String): String = if (contentType.contains("png")) "png" else "jpg"

    private fun fullKey(key: String): String = props.appPrefix + key

    companion object {
        private val EXTENSIONS = listOf("jpg", "png")
        private val URL_TTL: Duration = Duration.ofHours(1)
    }
}
