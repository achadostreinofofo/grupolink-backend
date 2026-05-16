package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.AuthResponse
import com.whatsappgroups.application.dto.LoginRequest
import com.whatsappgroups.application.dto.SignUpRequest
import com.whatsappgroups.application.usecase.auth.AuthUseCase
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.security.RsaKeyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Autenticação", description = "Signup, login e perfil do usuário")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authUseCase: AuthUseCase,
    private val userRepository: UserRepository,
    private val rsaKeyService: RsaKeyService
) {

    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody request: SignUpRequest): ResponseEntity<AuthResponse> {
        val decrypted = request.copy(
            password = rsaKeyService.decrypt(request.password),
            cpf = request.cpf?.let { rsaKeyService.decrypt(it) }
        )
        require(decrypted.password.length >= 8) { "Senha deve ter no mínimo 8 caracteres" }
        return ResponseEntity.status(HttpStatus.CREATED).body(authUseCase.signUp(decrypted))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val decrypted = request.copy(password = rsaKeyService.decrypt(request.password))
        return ResponseEntity.ok(authUseCase.login(decrypted))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userDetails: UserDetails): ResponseEntity<Any> {
        val userId = UUID.fromString(userDetails.username)
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        return ResponseEntity.ok(mapOf(
            "id" to user.id,
            "email" to user.email,
            "name" to user.name,
            "cpf" to user.cpf,
            "plan" to user.plan.name,
            "whatsappIntegrated" to user.whatsappIntegrated,
            "createdAt" to user.createdAt
        ))
    }
}
