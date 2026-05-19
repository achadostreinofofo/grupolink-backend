package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.MlOAuthStartResponse
import com.whatsappgroups.application.dto.MlStatusResponse
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.view.RedirectView
import java.util.UUID

@RestController
@RequestMapping("/api/ml")
class MercadoLivreOAuthController(
    private val mlAccountUseCase: MercadoLivreAccountUseCase,
    private val jwtTokenProvider: JwtTokenProvider
) {
    @GetMapping("/status")
    fun getStatus(@RequestHeader("Authorization") token: String): ResponseEntity<MlStatusResponse> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        val (connected, nickname) = mlAccountUseCase.getStatus(userId)
        return ResponseEntity.ok(MlStatusResponse(connected, nickname))
    }

    @GetMapping("/oauth/start")
    fun startOAuth(@RequestHeader("Authorization") token: String): ResponseEntity<MlOAuthStartResponse> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        val authUrl = mlAccountUseCase.getOAuthUrl(userId)
        return ResponseEntity.ok(MlOAuthStartResponse(authUrl))
    }

    @GetMapping("/oauth/callback")
    fun oauthCallback(
        @RequestParam code: String,
        @RequestParam state: String,
        @RequestParam(required = false) error: String?
    ): RedirectView {
        if (error != null) {
            return RedirectView("/dashboard/settings/integrations?ml=error&error_desc=$error")
        }

        return try {
            mlAccountUseCase.handleCallback(code, state)
            RedirectView("/dashboard/settings/integrations?ml=success")
        } catch (e: Exception) {
            RedirectView("/dashboard/settings/integrations?ml=error&error_desc=${e.message}")
        }
    }

    @DeleteMapping("/disconnect")
    fun disconnect(@RequestHeader("Authorization") token: String): ResponseEntity<Void> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        mlAccountUseCase.disconnect(userId)
        return ResponseEntity.noContent().build()
    }
}
