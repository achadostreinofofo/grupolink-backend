package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.usecase.export.ExportMembersUseCase
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/structures/{structureId}/export")
class ExportController(private val exportMembersUseCase: ExportMembersUseCase) {

    @GetMapping("/members.csv")
    fun exportMembers(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable structureId: UUID
    ): ResponseEntity<ByteArray> {
        val csv = exportMembersUseCase.exportMembersCsv(UUID.fromString(user.username), structureId)
        return csvResponse(csv, "membros_${LocalDate.now()}.csv")
    }

    @GetMapping("/redirects.csv")
    fun exportRedirects(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable structureId: UUID
    ): ResponseEntity<ByteArray> {
        val csv = exportMembersUseCase.exportRedirectsCsv(UUID.fromString(user.username), structureId)
        return csvResponse(csv, "redirects_${LocalDate.now()}.csv")
    }

    private fun csvResponse(data: ByteArray, filename: String): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
            .body(data)
}
