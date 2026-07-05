package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.AnalyticsOverviewResponse
import com.whatsappgroups.application.usecase.analytics.AnalyticsUseCase
import com.whatsappgroups.domain.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController(
    private val analyticsUseCase: AnalyticsUseCase,
    private val userRepository: UserRepository
) {
    @GetMapping("/overview")
    fun getOverview(request: HttpServletRequest): ResponseEntity<Any> {
        val userId = request.getAttribute("userId") as? String
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Não autenticado"))

        val user = userRepository.findById(UUID.fromString(userId)).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Usuário não encontrado"))

        if (!user.isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Acesso restrito a administradores"))
        }

        return ResponseEntity.ok(analyticsUseCase.getOverview())
    }
}
