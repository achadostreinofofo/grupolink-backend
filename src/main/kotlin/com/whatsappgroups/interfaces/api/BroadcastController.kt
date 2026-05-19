package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.application.usecase.whatsapp.BroadcastUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/structures/{structureId}/broadcast")
class BroadcastController(private val broadcastUseCase: BroadcastUseCase) {

    @PostMapping
    fun broadcast(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable structureId: UUID,
        @Valid @RequestBody request: BroadcastMessageRequest
    ): ResponseEntity<BroadcastMessageResponse> =
        ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(broadcastUseCase.broadcast(UUID.fromString(user.username), structureId, request))

    @GetMapping("/{broadcastId}")
    fun getStatus(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable structureId: UUID,
        @PathVariable broadcastId: UUID
    ): ResponseEntity<BroadcastStatusResponse> =
        ResponseEntity.ok(broadcastUseCase.getStatus(UUID.fromString(user.username), broadcastId))
}

@RestController
@RequestMapping("/api/broadcasts")
class BroadcastListController(private val broadcastUseCase: BroadcastUseCase) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<BroadcastStatusResponse>> =
        ResponseEntity.ok(broadcastUseCase.listByUser(UUID.fromString(user.username)))
}
