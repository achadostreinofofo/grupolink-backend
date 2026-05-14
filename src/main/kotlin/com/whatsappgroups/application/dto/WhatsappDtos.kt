package com.whatsappgroups.application.dto

import jakarta.validation.constraints.NotBlank

data class ConnectWhatsappRequest(
    @field:NotBlank val phoneNumberId: String,
    @field:NotBlank val accessToken: String
)

data class WhatsappAccountResponse(
    val id: String,
    val phoneNumberId: String,
    val displayName: String?,
    val displayPhone: String?,
    val active: Boolean
)
