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

// ──────── Phone Number Check ────────

data class CheckPhoneRequest(
    @field:NotBlank val phone: String  // DDD + número, ex: "11999998888"
)

data class CheckPhoneResponse(
    val phone: String,
    val exists: Boolean,
    val formattedPhone: String,  // ex: "5511999998888"
    val jid: String              // JID exato do WhatsApp para uso em groupCreate
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
