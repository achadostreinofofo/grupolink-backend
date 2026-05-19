package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.AddGroupRequest
import com.whatsappgroups.application.dto.CreateStructureRequest
import com.whatsappgroups.application.dto.GroupResponse
import com.whatsappgroups.application.dto.StructureResponse
import com.whatsappgroups.application.usecase.structure.StructureUseCase
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Estruturas", description = "Containers de grupos WhatsApp com smart links")
@RestController
@RequestMapping("/api/structures")
class StructureController(private val structureUseCase: StructureUseCase) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: CreateStructureRequest
    ): ResponseEntity<StructureResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(structureUseCase.create(UUID.fromString(user.username), request))

    @GetMapping
    fun listAll(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<StructureResponse>> =
        ResponseEntity.ok(structureUseCase.listByUser(UUID.fromString(user.username)))

    @GetMapping("/{id}")
    fun getById(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<StructureResponse> =
        ResponseEntity.ok(structureUseCase.getById(UUID.fromString(user.username), id))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        structureUseCase.delete(UUID.fromString(user.username), id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/groups")
    fun addGroup(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddGroupRequest
    ): ResponseEntity<GroupResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(structureUseCase.addGroup(UUID.fromString(user.username), id, request))
}
