package com.whatsappgroups.application.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class SignUpRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    @field:NotBlank val name: String,
    @field:Pattern(
        regexp = "^\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}$|^\\d{11}$",
        message = "CPF inválido. Use o formato 000.000.000-00 ou apenas os 11 dígitos"
    )
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
    val plan: String,
    val emailVerified: Boolean = true
)
