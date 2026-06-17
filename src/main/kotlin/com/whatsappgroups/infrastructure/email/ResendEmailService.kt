package com.whatsappgroups.infrastructure.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

// Transporte de e-mail via Resend (https://resend.com). Provedor padrão — ativo quando
// app.email.provider=resend ou ausente. Envio por HTTP simples (POST /emails).
@Service
@ConditionalOnProperty(name = ["app.email.provider"], havingValue = "resend", matchIfMissing = true)
class ResendEmailService(
    @Value("\${app.resend.api-key:}") private val apiKey: String,
    @Value("\${app.resend.from:Redirect Grupo <noreply@redirectgrupo.com.br>}") private val fromAddress: String
) : AbstractEmailService() {

    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.resend.com")
        .build()

    override fun send(to: String, subject: String, html: String, text: String, replyTo: String?) {
        if (apiKey.isBlank()) {
            log.error("RESEND_API_KEY não configurada — e-mail '$subject' para $to NÃO enviado")
            throw IllegalStateException("Serviço de e-mail não configurado.")
        }

        val body = buildMap<String, Any> {
            put("from", fromAddress)
            put("to", listOf(to))
            put("subject", subject)
            put("html", html)
            put("text", text)
            if (replyTo != null) put("reply_to", replyTo)
        }

        try {
            webClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono<String>()
                .block()
            log.info("Email '$subject' sent to $to via Resend")
        } catch (e: Exception) {
            log.error("Failed to send email '$subject' to $to via Resend: ${e.message}")
            throw e
        }
    }
}
