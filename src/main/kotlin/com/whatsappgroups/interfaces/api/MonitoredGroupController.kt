package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.application.usecase.whatsapp.MonitoredGroupUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/monitored-groups")
class MonitoredGroupController(
    private val useCase: MonitoredGroupUseCase
) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<MonitoredGroupResponse>> =
        ResponseEntity.ok(useCase.list(UUID.fromString(user.username)))

    @PostMapping
    fun create(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: CreateMonitoredGroupRequest
    ): ResponseEntity<MonitoredGroupResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(useCase.create(UUID.fromString(user.username), request))

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @RequestBody request: UpdateMonitoredGroupRequest
    ): ResponseEntity<MonitoredGroupResponse> =
        ResponseEntity.ok(useCase.update(UUID.fromString(user.username), id, request))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        useCase.delete(UUID.fromString(user.username), id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Lista os grupos WhatsApp que a sessão participa, com flag indicando
     * quais já estão sendo monitorados pelo usuário.
     */
    @GetMapping("/available")
    fun listAvailable(
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam sessionId: String
    ): ResponseEntity<List<AvailableGroupResponse>> =
        ResponseEntity.ok(useCase.listAvailableGroups(UUID.fromString(user.username), sessionId))
}
