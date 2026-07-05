package com.whatsappgroups.application.dto

import java.time.LocalDateTime

data class AdminLoginRequest(val email: String, val password: String)

data class AdminLoginResponse(val token: String, val name: String, val email: String)

data class AdminMeResponse(val id: String, val name: String, val email: String)

data class AdminUserListItem(
    val id: String,
    val name: String,
    val email: String,
    val plan: String,
    val status: String,
    val createdAt: LocalDateTime
)
