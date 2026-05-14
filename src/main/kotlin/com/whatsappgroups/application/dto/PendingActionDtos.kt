package com.whatsappgroups.application.dto

data class PendingActionResponse(
    val groupId: String,
    val groupName: String,
    val structureId: String,
    val structureName: String,
    val structureSlug: String,
    val sortOrder: Int,
    val createdAt: String
)

data class UpdateGroupRequest(
    val name: String? = null,
    val inviteLink: String? = null,
    val maxMembers: Int? = null,
    // Ao fornecer um inviteLink válido, o grupo é ativado automaticamente (CREATING → ACTIVE)
)
