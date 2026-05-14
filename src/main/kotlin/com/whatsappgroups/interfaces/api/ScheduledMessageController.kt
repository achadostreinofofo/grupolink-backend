package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.CreateScheduledMessageRequest
import com.whatsappgroups.application.dto.ScheduledMessageResponse
import com.whatsappgroups.application.usecase.message.ScheduledMessageUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/messages")
class ScheduledMessageController(private val messageUseCase: ScheduledMessageUseCase) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: CreateScheduledMessageRequest
    ): ResponseEntity<ScheduledMessageResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(messageUseCase.create(UUID.fromString(user.username), request))

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<ScheduledMessageResponse>> =
        ResponseEntity.ok(messageUseCase.listByUser(UUID.fromString(user.username)))

    @DeleteMapping("/{id}")
    fun cancel(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        messageUseCase.cancel(UUID.fromString(user.username), id)
        return ResponseEntity.noContent().build()
    }
}
