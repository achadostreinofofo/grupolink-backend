package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.application.usecase.analytics.AnalyticsUseCase
import com.whatsappgroups.domain.repository.AdminUserRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val adminUserRepository: AdminUserRepository,
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val passwordEncoder: PasswordEncoder,
    private val analyticsUseCase: AnalyticsUseCase,
) {

    @PostMapping("/auth/login")
    fun login(@RequestBody req: AdminLoginRequest): ResponseEntity<Any> {
        val admin = adminUserRepository.findByEmail(req.email)
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Credenciais inválidas"))

        if (!passwordEncoder.matches(req.password, admin.passwordHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("error" to "Credenciais inválidas"))
        }

        val token = jwtTokenProvider.generateAdminToken(admin.id!!)
        return ResponseEntity.ok(AdminLoginResponse(token = token, name = admin.name, email = admin.email))
    }

    @GetMapping("/auth/me")
    fun me(request: HttpServletRequest): ResponseEntity<Any> {
        val adminId = request.getAttribute("adminId") as? String
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Não autenticado"))

        val admin = adminUserRepository.findById(UUID.fromString(adminId)).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Admin não encontrado"))

        return ResponseEntity.ok(AdminMeResponse(id = admin.id.toString(), name = admin.name, email = admin.email))
    }

    @GetMapping("/metrics")
    fun metrics(): ResponseEntity<AnalyticsOverviewResponse> =
        ResponseEntity.ok(analyticsUseCase.getOverview())

    @GetMapping("/users")
    fun users(): ResponseEntity<List<AdminUserListItem>> {
        val list = userRepository.findAll().map { u ->
            AdminUserListItem(
                id        = u.id.toString(),
                name      = u.name,
                email     = u.email,
                plan      = u.plan.name,
                status    = u.status.name,
                createdAt = u.createdAt
            )
        }
        return ResponseEntity.ok(list)
    }
}
