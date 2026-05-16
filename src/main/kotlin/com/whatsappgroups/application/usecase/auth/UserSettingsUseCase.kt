package com.whatsappgroups.application.usecase.auth

import com.whatsappgroups.domain.repository.UserRepository
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

data class UpdateProfileRequest(
    @field:NotBlank val name: String,
    @field:Email @field:NotBlank val email: String,
    val cpf: String? = null
)

data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank @field:Size(min = 8) val newPassword: String
)

data class UserProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val cpf: String?,
    val plan: String,
    val whatsappIntegrated: Boolean,
    val hasPassword: Boolean,
    val createdAt: String,
    val trialEndsAt: String?,
    val isOnTrial: Boolean
)

@Service
class UserSettingsUseCase(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun getProfile(userId: UUID): UserProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }
        return user.toProfile()
    }

    @Transactional
    fun updateProfile(userId: UUID, request: UpdateProfileRequest): UserProfileResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        // Verifica se o e-mail já está em uso por outro usuário
        if (request.email != user.email && userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("E-mail '${request.email}' já está em uso")
        }

        user.name      = request.name
        user.cpf       = request.cpf?.replace(Regex("[^\\d]"), "")
                                     ?.let { if (it.length == 11) "${it.substring(0,3)}.${it.substring(3,6)}.${it.substring(6,9)}-${it.substring(9)}" else null }
                         ?: request.cpf
        user.updatedAt = LocalDateTime.now()

        // Atualiza e-mail apenas se mudou (OAuth users também podem atualizar)
        if (request.email != user.email) {
            (user as com.whatsappgroups.domain.model.User).let {
                val field = it.javaClass.getDeclaredField("email")
                field.isAccessible = true
                field.set(it, request.email)
            }
        }

        return user.toProfile()
    }

    @Transactional
    fun changePassword(userId: UUID, request: ChangePasswordRequest) {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        if (user.passwordHash.isBlank()) {
            throw IllegalStateException("Sua conta foi criada via Google. Defina uma senha na primeira vez sem informar a senha atual.")
        }

        if (!passwordEncoder.matches(request.currentPassword, user.passwordHash)) {
            throw IllegalArgumentException("Senha atual incorreta")
        }

        user.passwordHash = passwordEncoder.encode(request.newPassword)
        user.updatedAt    = LocalDateTime.now()
    }

    @Transactional
    fun setPasswordForOAuthUser(userId: UUID, newPassword: String) {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        if (user.passwordHash.isNotBlank()) {
            throw IllegalStateException("Use o endpoint de troca de senha para alterar a senha existente")
        }

        user.passwordHash = passwordEncoder.encode(newPassword)
        user.updatedAt    = LocalDateTime.now()
    }

    private fun com.whatsappgroups.domain.model.User.toProfile() = UserProfileResponse(
        id                 = id.toString(),
        name               = name,
        email              = email,
        cpf                = cpf,
        plan               = plan.name,
        whatsappIntegrated = whatsappIntegrated,
        hasPassword        = passwordHash.isNotBlank(),
        createdAt          = createdAt.toString(),
        trialEndsAt        = trialEndsAt?.toString(),
        isOnTrial          = trialEndsAt != null && java.time.LocalDateTime.now().isBefore(trialEndsAt)
    )
}
