package com.whatsappgroups.application.dto

import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

data class CreateScheduledMessageRequest(
    @field:NotBlank val title: String,
    @field:NotBlank val content: String,
    val mediaUrl: String? = null,
    val structureId: String? = null,
    val scheduledAt: LocalDateTime? = null   // null = DRAFT
)

data class UpdateScheduledMessageRequest(
    @field:NotBlank val title: String,
    @field:NotBlank val content: String,
    val mediaUrl: String? = null,
    val scheduledAt: LocalDateTime? = null
)

data class ScheduledMessageResponse(
    val id: String,
    val title: String,
    val content: String,
    val mediaUrl: String?,
    val status: String,
    val scheduledAt: String?,
    val executedAt: String?,
    val errorMessage: String?,
    val structureId: String?,
    val structureName: String?
)
