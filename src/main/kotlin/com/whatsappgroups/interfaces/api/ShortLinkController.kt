package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.usecase.shortlink.CreateShortLinkRequest
import com.whatsappgroups.application.usecase.shortlink.ShortLinkResponse
import com.whatsappgroups.application.usecase.shortlink.ShortLinkUseCase
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class ShortLinkController(private val shortLinkUseCase: ShortLinkUseCase) {

    // --- Endpoints autenticados (gestão) ---

    @PostMapping("/api/links")
    fun create(
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody request: CreateShortLinkRequest
    ): ResponseEntity<ShortLinkResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(shortLinkUseCase.create(UUID.fromString(user.username), request))

    @GetMapping("/api/links")
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<ShortLinkResponse>> =
        ResponseEntity.ok(shortLinkUseCase.listByUser(UUID.fromString(user.username)))

    @DeleteMapping("/api/links/{id}")
    fun delete(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        shortLinkUseCase.delete(UUID.fromString(user.username), id)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/api/links/{id}/toggle")
    fun toggle(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<ShortLinkResponse> =
        ResponseEntity.ok(shortLinkUseCase.toggleActive(UUID.fromString(user.username), id))

    // --- Redirect público (sem autenticação) ---

    @GetMapping("/s/{code}")
    fun redirect(@PathVariable code: String, response: HttpServletResponse) {
        val targetUrl = shortLinkUseCase.resolve(code)
        // Suppress the Referer so the Mercado Livre product opens as a direct navigation.
        // With an external referer, ML shows an "acesse sua conta" gate instead of the product.
        response.setHeader("Referrer-Policy", "no-referrer")
        response.status = HttpServletResponse.SC_FOUND
        response.setHeader("Location", targetUrl)
    }
}
