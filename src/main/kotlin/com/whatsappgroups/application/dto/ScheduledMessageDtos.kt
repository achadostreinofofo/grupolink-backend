package com.whatsappgroups.application.dto

import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class CreateScheduledMessageRequest(
    @field:NotBlank val title: String,
    @field:NotBlank val content: String,
    val mediaUrl: String? = null,
    val structureId: String? = null,
    @field:NotNull @field:Future val scheduledAt: LocalDateTime
)

data class ScheduledMessageResponse(
    val id: String,
    val title: String,
    val content: String,
    val mediaUrl: String?,
    val status: String,
    val scheduledAt: String,
    val executedAt: String?,
    val errorMessage: String?,
    val structureId: String?,
    val structureName: String?
)
