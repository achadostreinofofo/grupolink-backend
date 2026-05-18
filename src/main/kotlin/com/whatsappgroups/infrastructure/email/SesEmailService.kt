package com.whatsappgroups.infrastructure.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.*

@Service
class SesEmailService(
    @Value("\${app.ses.region:us-east-1}")    private val region: String,
    @Value("\${app.ses.access-key:}")         private val accessKey: String,
    @Value("\${app.ses.secret-key:}")         private val secretKey: String,
    @Value("\${app.ses.from:noreply@redirectgrupo.com.br}") private val fromAddress: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val ses: SesClient by lazy {
        val builder = SesClient.builder().region(Region.of(region))
        if (accessKey.isNotBlank() && secretKey.isNotBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            )
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create())
        }
        builder.build()
    }

    fun sendContactEmail(
        toAddress: String,
        senderName: String,
        senderEmail: String,
        messageBody: String
    ) {
        val subject = "Novo contato via site — $senderName"
        val html = """
            <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px">
              <h2 style="color:#0d9488">Nova mensagem de contato</h2>
              <table style="width:100%;border-collapse:collapse">
                <tr><td style="padding:6px 0;font-weight:bold;width:120px">Nome:</td><td>${escapeHtml(senderName)}</td></tr>
                <tr><td style="padding:6px 0;font-weight:bold">E-mail:</td><td><a href="mailto:${escapeHtml(senderEmail)}">${escapeHtml(senderEmail)}</a></td></tr>
              </table>
              <hr style="margin:16px 0;border:none;border-top:1px solid #e5e7eb"/>
              <h3 style="margin-bottom:8px">Mensagem:</h3>
              <p style="background:#f9fafb;border-left:4px solid #0d9488;padding:12px 16px;border-radius:4px;white-space:pre-wrap">${escapeHtml(messageBody)}</p>
              <p style="font-size:11px;color:#9ca3af;margin-top:24px">Enviado via formulário em redirectgrupo.com.br</p>
            </body></html>
        """.trimIndent()

        val text = "De: $senderName <$senderEmail>\n\n$messageBody"

        try {
            ses.sendEmail(
                SendEmailRequest.builder()
                    .destination(Destination.builder().toAddresses(toAddress).build())
                    .message(
                        Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(
                                Body.builder()
                                    .html(Content.builder().data(html).charset("UTF-8").build())
                                    .text(Content.builder().data(text).charset("UTF-8").build())
                                    .build()
                            )
                            .build()
                    )
                    .source(fromAddress)
                    .replyToAddresses(senderEmail)
                    .build()
            )
            log.info("Contact email sent from $senderEmail to $toAddress")
        } catch (e: Exception) {
            log.error("Failed to send contact email from $senderEmail: ${e.message}")
            throw e
        }
    }

    private fun escapeHtml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
