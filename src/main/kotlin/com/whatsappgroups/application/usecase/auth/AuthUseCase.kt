package com.whatsappgroups.application.usecase.auth

import com.whatsappgroups.application.dto.AuthResponse
import com.whatsappgroups.application.dto.LoginRequest
import com.whatsappgroups.application.dto.SignUpRequest
import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.repository.UserRepository
import java.time.LocalDateTime
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthUseCase(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Transactional
    fun signUp(request: SignUpRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("E-mail já cadastrado")
        }

        val user = userRepository.save(
            User(
                email        = request.email,
                passwordHash = passwordEncoder.encode(request.password),
                name         = request.name,
                cpf          = request.cpf?.replace(Regex("[^\\d]"), "")
                                          ?.let { "${it.substring(0,3)}.${it.substring(3,6)}.${it.substring(6,9)}-${it.substring(9)}" },
                plan         = Plan.SMART,
                trialEndsAt  = LocalDateTime.now().plusDays(7)
            )
        )

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

    private fun buildAuthResponse(user: User) = AuthResponse(
        token = jwtTokenProvider.generateToken(user.id!!),
        userId = user.id.toString(),
        email = user.email,
        name = user.name,
        plan = user.plan.name
    )
}
