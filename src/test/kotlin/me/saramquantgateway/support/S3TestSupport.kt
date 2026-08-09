package me.saramquantgateway.support

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.saramquantgateway.infra.storage.s3.S3KvStore
import me.saramquantgateway.infra.storage.s3.S3StorageProperties
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import java.util.UUID

// 스토어 통합 테스트용 S3 클라이언트/프리픽스 헬퍼 (app-test/ 하위만 사용).
object S3TestSupport {

    const val BUCKET = "saramquant-bucket"
    const val REGION = "ap-northeast-2"

    fun newPrefix(): String = "app-test/${UUID.randomUUID()}/"

    fun newClient(): S3Client = S3Client.builder()
        .region(Region.of(REGION))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    System.getenv("SARAMQUANT_IAM_KEY_ACCESS"),
                    System.getenv("SARAMQUANT_IAM_KEY_SECRET"),
                ),
            ),
        )
        .build()

    fun newKvStore(s3: S3Client, prefix: String): S3KvStore {
        val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        return S3KvStore(s3, S3StorageProperties(BUCKET, prefix, REGION), objectMapper)
    }

    fun rawJson(s3: S3Client, prefix: String, logicalKey: String): String =
        s3.getObjectAsBytes(
            GetObjectRequest.builder().bucket(BUCKET).key(prefix + logicalKey).build(),
        ).asUtf8String()

    fun cleanup(s3: S3Client, prefix: String) {
        var token: String? = null
        while (true) {
            val response = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(BUCKET).prefix(prefix).continuationToken(token).build(),
            )
            val ids = response.contents().map { ObjectIdentifier.builder().key(it.key()).build() }
            if (ids.isNotEmpty()) {
                s3.deleteObjects(
                    DeleteObjectsRequest.builder()
                        .bucket(BUCKET).delete(Delete.builder().objects(ids).build()).build(),
                )
            }
            if (response.isTruncated != true) break
            token = response.nextContinuationToken()
        }
    }
}
