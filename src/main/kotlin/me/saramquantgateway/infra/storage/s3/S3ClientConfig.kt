package me.saramquantgateway.infra.storage.s3

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

// 로컬은 env 자격증명, EC2는 인스턴스 롤을 자동 선택하는 DefaultCredentialsProvider 사용
@Configuration
class S3ClientConfig(private val props: S3StorageProperties) {

    @Bean
    fun s3Client(): S3Client =
        S3Client.builder()
            .region(Region.of(props.region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build()

    @Bean
    fun s3Presigner(): S3Presigner =
        S3Presigner.builder()
            .region(Region.of(props.region))
            .credentialsProvider(DefaultCredentialsProvider.builder().build())
            .build()
}
