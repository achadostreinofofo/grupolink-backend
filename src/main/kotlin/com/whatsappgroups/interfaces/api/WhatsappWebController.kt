package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.application.usecase.whatsapp.WhatsappWebUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/whatsapp/web")
class WhatsappWebController(private val useCase: WhatsappWebUseCase) {

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
}
