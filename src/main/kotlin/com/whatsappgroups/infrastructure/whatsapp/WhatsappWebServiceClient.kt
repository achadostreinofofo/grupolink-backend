package com.whatsappgroups.infrastructure.whatsapp

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

data class WebServiceSessionStatus(
    val status: String,          // waiting_scan | authenticated | disconnected | not_found
    val qrBase64: String? = null,
    val phone: String? = null
)

data class WebServiceGroupResult(
    val groupId: String,
    val inviteLink: String
)

@Component
class WhatsappWebServiceClient(
    @Value("\${app.whatsapp-service.url:http://localhost:3001}") private val serviceUrl: String,
    @Value("\${app.whatsapp-service.secret:dev-secret-change-in-production}") private val secret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client = WebClient.builder()
        .baseUrl(serviceUrl)
        .defaultHeader("x-api-secret", secret)
        .build()

    fun createSession(sessionId: String): Boolean = runCatching {
        client.post()
            .uri("/sessions")
            .bodyValue(mapOf("sessionId" to sessionId))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block()
        true
    }.getOrElse {
        log.error("Failed to create WhatsApp Web session $sessionId: ${it.message}")
        false
    }

    fun getSessionStatus(sessionId: String): WebServiceSessionStatus = runCatching {
        val resp = client.get()
            .uri("/sessions/$sessionId")
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block() ?: return WebServiceSessionStatus("not_found")

        WebServiceSessionStatus(
            status    = resp["status"] as? String ?: "not_found",
            qrBase64  = resp["qrBase64"] as? String,
            phone     = resp["phone"] as? String
        )
    }.getOrElse {
        log.warn("Failed to get session status for $sessionId: ${it.message}")
        WebServiceSessionStatus("not_found")
    }

    fun deleteSession(sessionId: String): Boolean = runCatching {
        client.delete()
            .uri("/sessions/$sessionId")
            .retrieve()
            .bodyToMono<Void>()
            .block()
        true
    }.getOrElse {
        log.error("Failed to delete session $sessionId: ${it.message}")
        false
    }

    fun createGroup(sessionId: String, groupName: String): WebServiceGroupResult? = runCatching {
        val resp = client.post()
            .uri("/groups")
            .bodyValue(mapOf("sessionId" to sessionId, "groupName" to groupName))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block() ?: return null

        WebServiceGroupResult(
            groupId    = resp["groupId"] as? String ?: error("missing groupId"),
            inviteLink = resp["inviteLink"] as? String ?: error("missing inviteLink")
        )
    }.getOrElse {
        log.error("Failed to create WhatsApp group via session $sessionId: ${it.message}")
        null
    }

    fun sendTextMessage(sessionId: String, whatsappGroupId: String, text: String): Boolean = runCatching {
        client.post()
            .uri("/messages/text")
            .bodyValue(mapOf("sessionId" to sessionId, "groupId" to whatsappGroupId, "text" to text))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block()
        true
    }.getOrElse {
        log.error("Failed to send text via $sessionId to $whatsappGroupId: ${it.message}")
        false
    }

    fun sendImageMessage(sessionId: String, whatsappGroupId: String, imageUrl: String, caption: String?): Boolean = runCatching {
        client.post()
            .uri("/messages/image")
            .bodyValue(mapOf(
                "sessionId" to sessionId,
                "groupId"   to whatsappGroupId,
                "imageUrl"  to imageUrl,
                "caption"   to (caption ?: "")
            ))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block()
        true
    }.getOrElse {
        log.error("Failed to send image via $sessionId to $whatsappGroupId: ${it.message}")
        false
    }
}
