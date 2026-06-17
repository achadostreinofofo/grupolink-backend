package com.whatsappgroups.application.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class SignUpRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
    @field:NotBlank val name: String,
    val cpf: String? = null,
    val phone: String? = null
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

data class ResendVerificationRequest(
    @field:Email @field:NotBlank val email: String
)

data class ForgotPasswordRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val cpf: String
)

data class ResetPasswordRequest(
    @field:NotBlank val token: String,
    @field:NotBlank val newPassword: String
)

data class SignUpPendingResponse(
    val email: String,
    val message: String = "Conta criada com sucesso. Verifique seu e-mail para ativar."
)
