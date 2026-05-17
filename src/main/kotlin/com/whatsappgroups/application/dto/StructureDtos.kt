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
    val inviteLink: String? = null,
    val maxMembers: Int = 256
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
    val sortOrder: Int
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
    val smartLink: String
)
