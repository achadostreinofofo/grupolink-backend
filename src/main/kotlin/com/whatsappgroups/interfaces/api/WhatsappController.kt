package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.ConnectWhatsappRequest
import com.whatsappgroups.application.dto.WhatsappAccountResponse
import com.whatsappgroups.application.usecase.whatsapp.WhatsappAccountUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/whatsapp/accounts")
class WhatsappController(private val whatsappAccountUseCase: WhatsappAccountUseCase) {

    @PostMapping
    fun connect(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: ConnectWhatsappRequest
    ): ResponseEntity<WhatsappAccountResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(whatsappAccountUseCase.connect(UUID.fromString(user.username), request))

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<WhatsappAccountResponse>> =
        ResponseEntity.ok(whatsappAccountUseCase.listAccounts(UUID.fromString(user.username)))

    @DeleteMapping("/{accountId}")
    fun disconnect(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable accountId: UUID
    ): ResponseEntity<Void> {
        whatsappAccountUseCase.disconnect(UUID.fromString(user.username), accountId)
        return ResponseEntity.noContent().build()
    }
}
