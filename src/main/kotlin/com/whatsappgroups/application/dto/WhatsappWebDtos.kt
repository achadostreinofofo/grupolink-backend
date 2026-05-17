package com.whatsappgroups.application.dto

import jakarta.validation.constraints.NotBlank

// ──────── QR Session ────────

data class StartSessionResponse(
    val sessionId: String,
    val status: String   // WAITING_SCAN | AUTHENTICATED | DISCONNECTED
)

data class SessionStatusResponse(
    val sessionId: String,
    val status: String,   // WAITING_SCAN | AUTHENTICATED | DISCONNECTED
    val qrBase64: String?,
    val phone: String?
)

// ──────── Group Creation (via WhatsApp Web) ────────

data class CreateWhatsappGroupRequest(
    @field:NotBlank val name: String,
    val structureId: String? = null
)

data class CreateWhatsappGroupResponse(
    val whatsappGroupId: String,
    val inviteLink: String,
    val groupName: String
)

// ──────── Message Broadcasting ────────

data class BroadcastMessageRequest(
    val messageType: String = "TEXT",          // TEXT | IMAGE
    @field:NotBlank val content: String,
    val mediaUrl: String? = null,
    val groupIds: List<String>? = null         // null = all groups in structure
)

data class BroadcastMessageResponse(
    val broadcastId: String,
    val status: String,
    val totalGroups: Int
)

data class BroadcastStatusResponse(
    val broadcastId: String,
    val status: String,
    val totalGroups: Int,
    val groupsProcessed: Int,
    val groupsSuccessful: Int,
    val groupsFailed: Int,
    val createdAt: String
)
