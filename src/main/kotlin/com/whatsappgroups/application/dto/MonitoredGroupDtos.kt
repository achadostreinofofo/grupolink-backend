package com.whatsappgroups.application.dto

import jakarta.validation.constraints.NotBlank

// ── CRUD de grupos monitorados ───────────────────────────────────

data class CreateMonitoredGroupRequest(
    @field:NotBlank val sessionId: String,           // session do WhatsApp Web
    @field:NotBlank val whatsappGroupId: String,     // JID do grupo (ex: 123@g.us)
    val whatsappGroupName: String? = null,           // nome amigável do grupo
    @field:NotBlank val structureId: String,         // estrutura para onde redistribuir
    val messagePrefix: String? = null                // texto opcional prefixado em cada reenvio
)

data class UpdateMonitoredGroupRequest(
    val structureId: String? = null,
    val messagePrefix: String? = null,
    val active: Boolean? = null
)

data class MonitoredGroupResponse(
    val id: String,
    val sessionId: String,
    val whatsappGroupId: String,
    val whatsappGroupName: String?,
    val structureId: String,
    val structureName: String,
    val messagePrefix: String?,
    val active: Boolean,
    val createdAt: String
)

// ── Listagem de grupos disponíveis no whatsapp-service ──────────

data class AvailableGroupResponse(
    val groupId: String,
    val name: String,
    val participants: Int,
    val alreadyMonitored: Boolean
)

// ── Webhook: payload enviado pelo whatsapp-service ──────────────

data class IncomingWhatsappMessageRequest(
    @field:NotBlank val sessionId: String,
    @field:NotBlank val groupId: String,
    val senderJid: String? = null,
    val messageId: String? = null,
    @field:NotBlank val text: String,
    val imageBase64: String? = null,  // buffer da imagem em base64, quando presente
    val imageMimeType: String? = null,
    val timestamp: Long? = null
)
