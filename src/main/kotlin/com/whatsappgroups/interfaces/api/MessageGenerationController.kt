package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.dto.GenerateMessageResponse
import com.whatsappgroups.application.usecase.message.GenerateMessageFromLinkUseCase
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messages", description = "Geração e gestão de mensagens")
class MessageGenerationController(
    private val generateMessageFromLinkUseCase: GenerateMessageFromLinkUseCase,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @PostMapping("/generate-from-link")
    @Operation(summary = "Gera texto de mensagem a partir de link de produto do Mercado Livre usando IA")
    fun generateFromLink(
        @RequestHeader("Authorization") token: String,
        @Valid @RequestBody request: GenerateMessageRequest
    ): ResponseEntity<GenerateMessageResponse> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        return ResponseEntity.ok(generateMessageFromLinkUseCase.generate(userId, request))
    }
}
