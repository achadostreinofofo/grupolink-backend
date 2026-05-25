package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.MlAffiliateParamsRequest
import com.whatsappgroups.application.dto.MlItemDetails
import com.whatsappgroups.application.dto.MlOAuthStartResponse
import com.whatsappgroups.application.dto.MlStatusResponse
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.view.RedirectView

@RestController
@RequestMapping("/api/ml")
class MercadoLivreOAuthController(
    private val mlAccountUseCase: MercadoLivreAccountUseCase,
    private val mlApiClient: MercadoLivreApiClient,
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${app.frontend-url:https://www.redirectgrupo.com.br}") private val frontendUrl: String
) {
    @GetMapping("/status")
    fun getStatus(@RequestHeader("Authorization") token: String): ResponseEntity<MlStatusResponse> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        return ResponseEntity.ok(mlAccountUseCase.getStatus(userId))
    }

    @PutMapping("/affiliate-params")
    fun saveAffiliateParams(
        @RequestHeader("Authorization") token: String,
        @RequestBody request: MlAffiliateParamsRequest
    ): ResponseEntity<Void> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        mlAccountUseCase.saveAffiliateParams(userId, request)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/resolve-affiliate")
    fun resolveAffiliate(
        @RequestHeader("Authorization") token: String,
        @RequestParam url: String
    ): ResponseEntity<Map<String, String>> {
        jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))
            ?: return ResponseEntity.status(401).build()
        val (mattWord, mattTool) = mlApiClient.resolveAffiliateParams(url)
        return if (mattWord != null && mattTool != null)
            ResponseEntity.ok(mapOf("mattWord" to mattWord, "mattTool" to mattTool))
        else
            ResponseEntity.notFound().build()
    }

    @GetMapping("/items/{itemId}")
    fun getItem(
        @PathVariable itemId: String,
        @RequestHeader("Authorization") token: String
    ): ResponseEntity<MlItemDetails> {
        jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))
            ?: return ResponseEntity.status(401).build()
        return mlApiClient.getItem(itemId)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()
    }

    @GetMapping("/oauth/start")
    fun startOAuth(@RequestHeader("Authorization") token: String): ResponseEntity<MlOAuthStartResponse> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        return ResponseEntity.ok(MlOAuthStartResponse(mlAccountUseCase.getOAuthUrl(userId)))
    }

    @GetMapping("/oauth/callback")
    fun oauthCallback(
        @RequestParam code: String,
        @RequestParam state: String,
        @RequestParam(required = false) error: String?
    ): RedirectView {
        if (error != null) {
            return RedirectView("$frontendUrl/dashboard/settings/integrations?ml=error&error_desc=$error")
        }
        return try {
            mlAccountUseCase.handleCallback(code, state)
            RedirectView("$frontendUrl/dashboard/settings/integrations?ml=success")
        } catch (e: Exception) {
            RedirectView("$frontendUrl/dashboard/settings/integrations?ml=error&error_desc=${e.message}")
        }
    }

    @DeleteMapping("/disconnect")
    fun disconnect(@RequestHeader("Authorization") token: String): ResponseEntity<Void> {
        val userId = jwtTokenProvider.extractUserId(token.removePrefix("Bearer "))!!
        mlAccountUseCase.disconnect(userId)
        return ResponseEntity.noContent().build()
    }
}
