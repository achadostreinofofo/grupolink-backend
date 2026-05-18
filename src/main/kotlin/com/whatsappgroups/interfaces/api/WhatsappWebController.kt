package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.application.usecase.whatsapp.WhatsappWebUseCase
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/whatsapp/web")
class WhatsappWebController(
    private val useCase: WhatsappWebUseCase,
    private val sessionRepository: WhatsappWebSessionRepository,
    private val userRepository: UserRepository,
    private val webServiceClient: WhatsappWebServiceClient
) {

    @PostMapping("/sessions")
    fun startSession(
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<StartSessionResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(useCase.startSession(UUID.fromString(user.username)))

    @GetMapping("/sessions/{sessionId}")
    fun getStatus(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable sessionId: String
    ): ResponseEntity<SessionStatusResponse> =
        ResponseEntity.ok(useCase.getSessionStatus(UUID.fromString(user.username), sessionId))

    @GetMapping("/sessions")
    fun listSessions(
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<List<SessionStatusResponse>> =
        ResponseEntity.ok(useCase.listSessions(UUID.fromString(user.username)))

    @DeleteMapping("/sessions/{sessionId}")
    fun disconnect(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable sessionId: String
    ): ResponseEntity<Void> {
        useCase.disconnectSession(UUID.fromString(user.username), sessionId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/check-number")
    fun checkNumber(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: CheckPhoneRequest
    ): ResponseEntity<CheckPhoneResponse> {
        val userId = UUID.fromString(user.username)
        val owner  = userRepository.getReferenceById(userId)
        val session = sessionRepository
            .findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED)
            .orElseThrow { IllegalStateException("Nenhuma sessão WhatsApp autenticada. Conecte via QR Code primeiro.") }

        val normalized = "55${request.phone.replace(Regex("\\D"), "")}"
        val result = webServiceClient.checkPhoneNumber(session.sessionId, normalized)
            ?: throw IllegalStateException("Não foi possível verificar o número. Tente novamente.")

        return ResponseEntity.ok(
            CheckPhoneResponse(
                phone          = request.phone,
                exists         = result.exists,
                formattedPhone = result.phone,
                jid            = result.jid
            )
        )
    }
}
