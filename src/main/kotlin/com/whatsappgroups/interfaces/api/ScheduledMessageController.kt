package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.CreateScheduledMessageRequest
import com.whatsappgroups.application.dto.ScheduledMessageResponse
import com.whatsappgroups.application.dto.UpdateScheduledMessageRequest
import com.whatsappgroups.application.usecase.message.ScheduledMessageUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

// ── Mensagens por estrutura ──────────────────────────────────────

@RestController
@RequestMapping("/api/structures/{structureId}/messages")
class StructureMessageController(private val messageUseCase: ScheduledMessageUseCase) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable structureId: UUID,
        @Valid @RequestBody request: CreateScheduledMessageRequest
    ): ResponseEntity<ScheduledMessageResponse> {
        val payload = request.copy(structureId = structureId.toString())
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(messageUseCase.create(UUID.fromString(user.username), payload))
    }

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable structureId: UUID
    ): ResponseEntity<List<ScheduledMessageResponse>> =
        ResponseEntity.ok(messageUseCase.listByStructure(UUID.fromString(user.username), structureId))
}

data class SendNowRequest(val groupIds: List<String>? = null)

// ── Operações por mensagem ───────────────────────────────────────

@RestController
@RequestMapping("/api/messages")
class ScheduledMessageController(private val messageUseCase: ScheduledMessageUseCase) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<ScheduledMessageResponse>> =
        ResponseEntity.ok(messageUseCase.listByUser(UUID.fromString(user.username)))

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateScheduledMessageRequest
    ): ResponseEntity<ScheduledMessageResponse> =
        ResponseEntity.ok(messageUseCase.update(UUID.fromString(user.username), id, request))

    @PostMapping("/{id}/send")
    fun sendNow(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @RequestBody(required = false) body: SendNowRequest?
    ): ResponseEntity<ScheduledMessageResponse> =
        ResponseEntity.ok(messageUseCase.sendNow(UUID.fromString(user.username), id, body?.groupIds))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        messageUseCase.delete(UUID.fromString(user.username), id)
        return ResponseEntity.noContent().build()
    }
}
