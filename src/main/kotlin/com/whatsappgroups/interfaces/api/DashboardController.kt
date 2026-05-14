package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.GroupResponse
import com.whatsappgroups.application.dto.PendingActionResponse
import com.whatsappgroups.application.dto.UpdateGroupRequest
import com.whatsappgroups.application.usecase.structure.GroupUpdateUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Dashboard", description = "Visão geral e ações pendentes")
@RestController
@RequestMapping("/api/dashboard")
class DashboardController(private val groupUpdateUseCase: GroupUpdateUseCase) {

    @Operation(summary = "Lista grupos com status CREATING que aguardam link de convite")
    @GetMapping("/pending-actions")
    fun pendingActions(
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<List<PendingActionResponse>> =
        ResponseEntity.ok(groupUpdateUseCase.getPendingActions(UUID.fromString(user.username)))

    @Operation(summary = "Atualiza grupo (nome, link de convite, capacidade). Ativar grupo CREATING: forneça um inviteLink.")
    @PutMapping("/structures/{structureId}/groups/{groupId}")
    fun updateGroup(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable structureId: UUID,
        @PathVariable groupId: UUID,
        @RequestBody request: UpdateGroupRequest
    ): ResponseEntity<GroupResponse> =
        ResponseEntity.ok(
            groupUpdateUseCase.updateGroup(UUID.fromString(user.username), structureId, groupId, request)
        )
}
