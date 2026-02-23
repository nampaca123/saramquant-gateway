package me.saramquantgateway.infra.aws.lib

import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.*

@Component
class AwsSesClient(private val props: AwsSesProperties) {

    private val client: SesV2Client = SesV2Client.builder()
        .region(Region.of(props.region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey, props.secretKey)
            )
        )
        .build()

    fun sendHtmlEmail(to: String, subject: String, htmlBody: String) {
        client.sendEmail(
            SendEmailRequest.builder()
                .fromEmailAddress(props.senderEmail)
                .destination(Destination.builder().toAddresses(to).build())
                .content(
                    EmailContent.builder().simple(
                        Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder().html(Content.builder().data(htmlBody).charset("UTF-8").build()).build())
                            .build()
                    ).build()
                )
                .build()
        )
    }
}
