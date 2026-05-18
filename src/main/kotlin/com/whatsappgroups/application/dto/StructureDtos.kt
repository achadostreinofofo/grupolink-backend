package com.whatsappgroups.application.dto

import jakarta.validation.constraints.NotBlank

data class CreateStructureRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val maxMembersPerGroup: Int = 256,
    val fillThreshold: Double = 0.80
)

data class AddGroupRequest(
    @field:NotBlank val name: String,
    val startingNumber: Int = 1,
    val profilePicUrl: String? = null,
    val participantJids: List<String>? = null  // JIDs exatos do WhatsApp (ex: "5511999998888@s.whatsapp.net")
)

data class GroupResponse(
    val id: String,
    val name: String,
    val inviteLink: String?,
    val memberCount: Int,
    val maxMembers: Int,
    val capacityPercentage: Double,
    val clickCount: Long,
    val status: String,
    val sortOrder: Int,
    val whatsappGroupId: String?   // null = grupo não criado no WhatsApp ainda
)

data class StructureResponse(
    val id: String,
    val name: String,
    val slug: String,
    val description: String?,
    val maxMembersPerGroup: Int,
    val fillThreshold: Double,
    val active: Boolean,
    val groups: List<GroupResponse>,
    val smartLink: String,
    val groupNamePrefix: String?,
    val nextGroupNumber: Int,
    val groupProfilePicUrl: String?
)
