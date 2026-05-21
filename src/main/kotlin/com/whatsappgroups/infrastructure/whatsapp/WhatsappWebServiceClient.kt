package com.whatsappgroups.infrastructure.whatsapp

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

data class WebServiceSessionStatus(
    val status: String,
    val qrBase64: String? = null,
    val phone: String? = null
)

data class WebServiceGroupResult(
    val groupId: String,
    val inviteLink: String
)

data class WebServiceGroupInfo(
    val groupId: String,
    val name: String,
    val participants: Int,
    val profilePicUrl: String? = null
)

data class WebServiceGroupDetail(
    val groupId: String,
    val name: String,
    val participants: Int,
    val inviteLink: String? = null,
    val profilePicUrl: String? = null
)

data class CheckPhoneResult(
    val exists: Boolean,
    val phone: String,
    val jid: String
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

    fun checkPhoneNumber(sessionId: String, phone: String): CheckPhoneResult {
        return runCatching {
            val resp = client.post()
                .uri("/check-number")
                .bodyValue(mapOf("sessionId" to sessionId, "phone" to phone))
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: throw IllegalStateException("Resposta vazia do WhatsApp Service")

            CheckPhoneResult(
                exists = resp["exists"] as? Boolean ?: false,
                phone  = resp["phone"] as? String ?: phone,
                jid    = resp["jid"] as? String ?: "${phone}@s.whatsapp.net"
            )
        }.getOrElse {
            log.warn("checkPhoneNumber failed for $phone via $sessionId: ${it.message}")
            throw IllegalStateException("Erro ao verificar número: ${it.message}")
        }
    }

    fun createGroup(sessionId: String, groupName: String, participants: List<String>, profilePicUrl: String?): WebServiceGroupResult {
        return runCatching {
            val body = mutableMapOf<String, Any>(
                "sessionId"    to sessionId,
                "groupName"    to groupName,
                "participants" to participants
            )
            if (profilePicUrl != null) body["profilePicUrl"] = profilePicUrl

            val resp = client.post()
                .uri("/groups")
                .bodyValue(body)
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block() ?: throw IllegalStateException("Resposta vazia do WhatsApp Service")

            WebServiceGroupResult(
                groupId    = resp["groupId"] as? String ?: error("missing groupId"),
                inviteLink = resp["inviteLink"] as? String ?: error("missing inviteLink")
            )
        }.getOrElse {
            log.error("createGroup failed for session $sessionId: ${it.message}")
            throw IllegalStateException("Erro ao criar grupo no WhatsApp: ${it.message}")
        }
    }

    fun getGroupInfo(sessionId: String, groupId: String): WebServiceGroupDetail? = runCatching {
        val resp = client.get()
            .uri { it.path("/sessions/{sessionId}/groups/{groupId}").build(sessionId, groupId) }
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block() ?: return null

        WebServiceGroupDetail(
            groupId       = resp["groupId"] as? String ?: groupId,
            name          = resp["name"] as? String ?: "",
            participants  = (resp["participants"] as? Number)?.toInt() ?: 0,
            inviteLink    = resp["inviteLink"] as? String,
            profilePicUrl = resp["profilePicUrl"] as? String
        )
    }.getOrElse {
        log.error("getGroupInfo failed for $groupId: ${it.message}")
        null
    }

    fun getGroupInviteLink(sessionId: String, groupId: String): String? = runCatching {
        val resp = client.get()
            .uri { it.path("/groups/{groupId}/invite-link").queryParam("sessionId", sessionId).build(groupId) }
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block() ?: return null

        resp["inviteLink"] as? String
    }.getOrElse {
        log.error("getGroupInviteLink failed for $groupId: ${it.message}")
        null
    }

    fun listGroups(sessionId: String, onlyOwned: Boolean = true): List<WebServiceGroupInfo> = runCatching {
        val resp = client.get()
            .uri("/sessions/$sessionId/groups?onlyOwned=$onlyOwned")
            .retrieve()
            .bodyToMono<List<Map<String, Any>>>()
            .block() ?: emptyList()

        resp.map { item ->
            WebServiceGroupInfo(
                groupId      = item["groupId"] as? String ?: "",
                name         = item["name"] as? String ?: "(sem nome)",
                participants = (item["participants"] as? Number)?.toInt() ?: 0
            )
        }.filter { it.groupId.isNotBlank() }
    }.getOrElse {
        log.warn("Failed to list groups for $sessionId: ${it.message}")
        emptyList()
    }

    fun getGroupParticipantCount(sessionId: String, groupId: String): Int? = runCatching {
        val resp = client.get()
            .uri { it.path("/groups/{groupId}/participants/count").queryParam("sessionId", sessionId).build(groupId) }
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block() ?: return null
        (resp["count"] as? Number)?.toInt()
    }.getOrElse {
        log.warn("Failed to get participant count for group $groupId via $sessionId: ${it.message}")
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

    fun sendImageBase64Message(sessionId: String, whatsappGroupId: String, imageBase64: String, caption: String?): Boolean = runCatching {
        client.post()
            .uri("/messages/image")
            .bodyValue(mapOf(
                "sessionId"   to sessionId,
                "groupId"     to whatsappGroupId,
                "imageBase64" to imageBase64,
                "caption"     to (caption ?: "")
            ))
            .retrieve()
            .bodyToMono<Map<String, Any>>()
            .block()
        true
    }.getOrElse {
        log.error("Failed to send image base64 via $sessionId to $whatsappGroupId: ${it.message}")
        false
    }
}
