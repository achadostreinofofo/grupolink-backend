package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.AnalyticsOverviewResponse
import com.whatsappgroups.application.usecase.analytics.AnalyticsUseCase
import com.whatsappgroups.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
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
    fun getOverview(@AuthenticationPrincipal userDetails: UserDetails?): ResponseEntity<Any> {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Não autenticado"))
        }

        val user = userRepository.findById(UUID.fromString(userDetails.username)).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Usuário não encontrado"))

        if (!user.isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Acesso restrito a administradores"))
        }

        return ResponseEntity.ok(analyticsUseCase.getOverview())
    }
}
