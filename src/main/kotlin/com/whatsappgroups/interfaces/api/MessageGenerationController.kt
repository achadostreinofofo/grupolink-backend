package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.dto.GenerateMessageResponse
import com.whatsappgroups.application.usecase.message.GenerateMessageFromLinkUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messages", description = "Geração e gestão de mensagens")
class MessageGenerationController(
    private val generateMessageFromLinkUseCase: GenerateMessageFromLinkUseCase
) {

    @PostMapping("/generate-from-link")
    @Operation(summary = "Gera texto de mensagem a partir de link de produto do Mercado Livre usando IA")
    fun generateFromLink(
        @Valid @RequestBody request: GenerateMessageRequest
    ): ResponseEntity<GenerateMessageResponse> =
        ResponseEntity.ok(generateMessageFromLinkUseCase.generate(request))
}
