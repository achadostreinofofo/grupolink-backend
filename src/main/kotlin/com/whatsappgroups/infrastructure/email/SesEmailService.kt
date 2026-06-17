package com.whatsappgroups.infrastructure.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.*

// Transporte de e-mail via AWS SES. Ativo apenas quando app.email.provider=ses.
@Service
@ConditionalOnProperty(name = ["app.email.provider"], havingValue = "ses")
class SesEmailService(
    private val awsCredentialsProvider: AwsCredentialsProvider,
    @Value("\${app.ses.region:\${app.s3.region:us-east-1}}") private val region: String,
    @Value("\${app.ses.from:noreply@redirectgrupo.com.br}")  private val fromAddress: String
) : AbstractEmailService() {

    private val log = LoggerFactory.getLogger(javaClass)

    private val ses: SesClient by lazy {
        SesClient.builder()
            .region(Region.of(region))
            .credentialsProvider(awsCredentialsProvider)
            .build()
    }

    override fun send(to: String, subject: String, html: String, text: String, replyTo: String?) {
        try {
            ses.sendEmail(buildRequest(to, subject, html, text, replyTo))
            log.info("Email '$subject' sent to $to via SES")
        } catch (e: Exception) {
            log.error("Failed to send email '$subject' to $to via SES: ${e.message}")
            throw e
        }
    }

    private fun buildRequest(
        to: String, subject: String, html: String,
        text: String = "", replyTo: String? = null
    ) = SendEmailRequest.builder()
        .destination(Destination.builder().toAddresses(to).build())
        .message(
            Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(Body.builder()
                    .html(Content.builder().data(html).charset("UTF-8").build())
                    .text(Content.builder().data(text).charset("UTF-8").build())
                    .build())
                .build()
        )
        .source(fromAddress)
        .apply { if (replyTo != null) replyToAddresses(replyTo) }
        .build()
}
