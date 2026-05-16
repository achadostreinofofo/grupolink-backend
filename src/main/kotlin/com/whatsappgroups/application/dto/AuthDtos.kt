package com.whatsappgroups.application.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

// password and cpf arrive RSA-OAEP encrypted — pattern/size constraints applied after decryption
data class SignUpRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    @field:NotBlank val name: String,
    val cpf: String? = null
)

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String
)

data class AuthResponse(
    val token: String,
    val userId: String,
    val email: String,
    val name: String,
    val plan: String
)
