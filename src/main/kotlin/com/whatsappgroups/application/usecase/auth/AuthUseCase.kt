package com.whatsappgroups.application.usecase.auth

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.model.UserStatus
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.email.EmailService
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
    private val emailService: EmailService,
    @Value("\${app.frontend-url:https://www.redirectgrupo.com.br}") private val frontendUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun signUp(request: SignUpRequest): SignUpPendingResponse {
        if (userRepository.existsByEmail(request.email)) throw EmailAlreadyExistsException()

        val formattedCpf = request.cpf
            ?.replace(Regex("[^\\d]"), "")
            ?.let { d -> "${d.substring(0,3)}.${d.substring(3,6)}.${d.substring(6,9)}-${d.substring(9)}" }

        if (formattedCpf != null && userRepository.existsByCpf(formattedCpf)) throw CpfAlreadyExistsException()

        val verificationToken = UUID.randomUUID().toString().replace("-", "")

        val user = userRepository.save(
            User(
                email                  = request.email,
                passwordHash           = passwordEncoder.encode(request.password),
                name                   = request.name,
                cpf                    = formattedCpf,
                phone                  = request.phone?.replace(Regex("[^\\d+]"), ""),
                status                 = UserStatus.PENDING_VERIFICATION,
                emailVerificationToken = verificationToken
            )
        )

        val activationLink = "$frontendUrl/verify-email?token=$verificationToken"
        try {
            emailService.sendVerificationEmail(user.email, user.name, activationLink)
        } catch (e: Exception) {
            log.error("Failed to send verification email to ${user.email}: ${e.message}")
        }

        return SignUpPendingResponse(email = user.email)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Credenciais inválidas")

        if (!passwordEncoder.matches(request.password, user.passwordHash))
            throw IllegalArgumentException("Credenciais inválidas")

        if (user.status == UserStatus.PENDING_VERIFICATION)
            throw EmailNotVerifiedException()

        return buildAuthResponse(user)
    }

    @Transactional
    fun resendVerification(request: ResendVerificationRequest) {
        val user = userRepository.findByEmail(request.email)
        // Não revelamos se a conta existe ou já está ativa — só reenviamos se estiver pendente.
        if (user == null || user.status != UserStatus.PENDING_VERIFICATION) {
            log.info("Resend verification: nada a fazer para ${request.email}")
            return
        }

        val verificationToken = UUID.randomUUID().toString().replace("-", "")
        user.emailVerificationToken = verificationToken
        user.updatedAt              = LocalDateTime.now()

        val activationLink = "$frontendUrl/verify-email?token=$verificationToken"
        try {
            emailService.sendVerificationEmail(user.email, user.name, activationLink)
        } catch (e: Exception) {
            log.error("Failed to resend verification email to ${user.email}: ${e.message}")
        }
    }

    @Transactional
    fun verifyEmail(token: String): AuthResponse {
        val user = userRepository.findByEmailVerificationToken(token)
            ?: throw IllegalArgumentException("Token de verificação inválido ou já utilizado")

        if (user.status == UserStatus.ACTIVE)
            return buildAuthResponse(user)

        user.status                 = UserStatus.ACTIVE
        user.emailVerifiedAt        = LocalDateTime.now()
        user.emailVerificationToken = null
        user.updatedAt              = LocalDateTime.now()

        return buildAuthResponse(user)
    }

    @Transactional
    fun forgotPassword(request: ForgotPasswordRequest) {
        val user = userRepository.findByEmail(request.email)
        // Não revelamos se o usuário existe — sempre retorna sucesso ao caller
        if (user == null) {
            log.warn("Forgot password: no account for email=${request.email}")
            return
        }

        val resetToken = UUID.randomUUID().toString().replace("-", "")
        user.passwordResetToken     = resetToken
        user.passwordResetExpiresAt = LocalDateTime.now().plusHours(2)
        user.updatedAt              = LocalDateTime.now()

        val resetLink = "$frontendUrl/reset-password?token=$resetToken"
        try {
            emailService.sendPasswordResetEmail(user.email, user.name, resetLink)
        } catch (e: Exception) {
            log.error("Failed to send password reset email to ${user.email}: ${e.message}")
        }
    }

    @Transactional
    fun resetPassword(request: ResetPasswordRequest) {
        require(request.newPassword.length >= 8) { "Senha deve ter no mínimo 8 caracteres" }

        val user = userRepository.findByPasswordResetToken(request.token)
            ?: throw IllegalArgumentException("Token inválido ou expirado")

        val expiresAt = user.passwordResetExpiresAt
        if (expiresAt == null || LocalDateTime.now().isAfter(expiresAt))
            throw IllegalArgumentException("Token inválido ou expirado")

        val now = LocalDateTime.now()
        user.passwordHash           = passwordEncoder.encode(request.newPassword)
        user.passwordResetToken     = null
        user.passwordResetExpiresAt = null
        user.passwordChangedAt      = now   // invalida JWTs emitidos antes do reset
        user.updatedAt              = now
    }

    fun buildAuthResponse(user: User) = AuthResponse(
        token  = jwtTokenProvider.generateToken(user.id!!),
        userId = user.id.toString(),
        email  = user.email,
        name   = user.name,
        plan   = user.plan.name
    )
}
