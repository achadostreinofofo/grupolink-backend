package com.whatsappgroups.application.usecase.auth

import com.whatsappgroups.application.dto.AuthResponse
import com.whatsappgroups.application.dto.LoginRequest
import com.whatsappgroups.application.dto.SignUpRequest
import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.email.EmailService
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class AuthUseCase(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val emailService: EmailService
) {

    @Transactional
    fun signUp(request: SignUpRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("E-mail já cadastrado")
        }

        val verifyToken = UUID.randomUUID().toString()

        val user = userRepository.save(
            User(
                email             = request.email,
                passwordHash      = passwordEncoder.encode(request.password),
                name              = request.name,
                cpf               = request.cpf?.replace(Regex("[^\\d]"), "")
                                              ?.let { "${it.substring(0,3)}.${it.substring(3,6)}.${it.substring(6,9)}-${it.substring(9)}" },
                plan              = Plan.SMART,
                trialEndsAt       = LocalDateTime.now().plusDays(7),
                emailVerified     = false,
                emailVerifyToken  = verifyToken,
                emailVerifyExp    = LocalDateTime.now().plusHours(24)
            )
        )

        // Envia e-mail de verificação de forma assíncrona
        emailService.sendVerificationEmail(user.email, user.name, verifyToken)

        return buildAuthResponse(user)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Credenciais inválidas")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("Credenciais inválidas")
        }

        return buildAuthResponse(user)
    }

    @Transactional
    fun verifyEmail(token: String) {
        val user = userRepository.findByEmailVerifyToken(token)
            ?: throw NoSuchElementException("Token de verificação inválido ou já utilizado")

        if (user.emailVerified) return  // já verificado

        val exp = user.emailVerifyExp
        if (exp == null || LocalDateTime.now().isAfter(exp)) {
            throw IllegalStateException("Token expirado. Solicite um novo e-mail de verificação.")
        }

        user.emailVerified    = true
        user.emailVerifyToken = null
        user.emailVerifyExp   = null
        user.updatedAt        = LocalDateTime.now()

        // Envia e-mail de boas-vindas após confirmação
        emailService.sendWelcomeEmail(user.email, user.name)
    }

    @Transactional
    fun resendVerification(userId: java.util.UUID) {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        if (user.emailVerified) throw IllegalStateException("E-mail já verificado")

        val newToken = UUID.randomUUID().toString()
        user.emailVerifyToken = newToken
        user.emailVerifyExp   = LocalDateTime.now().plusHours(24)
        user.updatedAt        = LocalDateTime.now()

        emailService.sendVerificationEmail(user.email, user.name, newToken)
    }

    private fun buildAuthResponse(user: User) = AuthResponse(
        token         = jwtTokenProvider.generateToken(user.id!!),
        userId        = user.id.toString(),
        email         = user.email,
        name          = user.name,
        plan          = user.plan.name,
        emailVerified = user.emailVerified
    )
}
