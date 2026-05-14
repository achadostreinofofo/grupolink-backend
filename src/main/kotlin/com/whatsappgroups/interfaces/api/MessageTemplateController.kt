package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.usecase.message.CreateTemplateRequest
import com.whatsappgroups.application.usecase.message.MessageTemplateResponse
import com.whatsappgroups.application.usecase.message.MessageTemplateUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/templates")
class MessageTemplateController(private val templateUseCase: MessageTemplateUseCase) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: CreateTemplateRequest
    ): ResponseEntity<MessageTemplateResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(templateUseCase.create(UUID.fromString(user.username), request))

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<MessageTemplateResponse>> =
        ResponseEntity.ok(templateUseCase.listByUser(UUID.fromString(user.username)))

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateTemplateRequest
    ): ResponseEntity<MessageTemplateResponse> =
        ResponseEntity.ok(templateUseCase.update(UUID.fromString(user.username), id, request))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        templateUseCase.delete(UUID.fromString(user.username), id)
        return ResponseEntity.noContent().build()
    }
}
