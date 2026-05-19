package com.whatsappgroups.application.dto

data class MlStatusResponse(
    val connected: Boolean,
    val nickname: String?
)

data class MlOAuthStartResponse(
    val authorizationUrl: String
)
