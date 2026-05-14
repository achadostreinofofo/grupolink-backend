package com.whatsappgroups.infrastructure.whatsapp

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

data class WhatsappTextMessage(
    val to: String,
    val text: String
)

data class WhatsappMediaMessage(
    val to: String,
    val mediaUrl: String,
    val caption: String? = null
)

@Component
class WhatsappCloudApiClient(
    @Value("\${app.whatsapp.api-version}") private val apiVersion: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = WebClient.builder()
        .baseUrl("https://graph.facebook.com")
        .build()

    fun sendTextMessage(phoneNumberId: String, accessToken: String, msg: WhatsappTextMessage): Boolean {
        val body = mapOf(
            "messaging_product" to "whatsapp",
            "recipient_type"    to "individual",
            "to"                to msg.to,
            "type"              to "text",
            "text"              to mapOf("preview_url" to false, "body" to msg.text)
        )
        return executePost(phoneNumberId, accessToken, body)
    }

    fun sendImageMessage(phoneNumberId: String, accessToken: String, msg: WhatsappMediaMessage): Boolean {
        val body = mapOf(
            "messaging_product" to "whatsapp",
            "recipient_type"    to "individual",
            "to"                to msg.to,
            "type"              to "image",
            "image"             to mapOf("link" to msg.mediaUrl, "caption" to (msg.caption ?: ""))
        )
        return executePost(phoneNumberId, accessToken, body)
    }

    fun verifyPhoneNumber(phoneNumberId: String, accessToken: String): WhatsappPhoneInfo? {
        return runCatching {
            webClient.get()
                .uri("/$apiVersion/$phoneNumberId?fields=display_phone_number,verified_name")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()
                ?.let {
                    WhatsappPhoneInfo(
                        phoneNumberId = phoneNumberId,
                        displayPhone  = it["display_phone_number"] as? String ?: "",
                        verifiedName  = it["verified_name"] as? String ?: ""
                    )
                }
        }.getOrElse {
            log.warn("Falha ao verificar número WhatsApp $phoneNumberId: ${it.message}")
            null
        }
    }

    private fun executePost(phoneNumberId: String, accessToken: String, body: Map<String, Any>): Boolean =
        runCatching {
            webClient.post()
                .uri("/$apiVersion/$phoneNumberId/messages")
                .header("Authorization", "Bearer $accessToken")
                .bodyValue(body)
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()
            true
        }.getOrElse {
            log.error("Falha ao enviar mensagem WhatsApp via $phoneNumberId: ${it.message}")
            false
        }
}

data class WhatsappPhoneInfo(
    val phoneNumberId: String,
    val displayPhone: String,
    val verifiedName: String
)
