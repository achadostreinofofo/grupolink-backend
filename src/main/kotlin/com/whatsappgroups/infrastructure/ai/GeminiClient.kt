package com.whatsappgroups.infrastructure.ai

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class GeminiClient(
    @Value("\${app.gemini.api-key:}") private val apiKey: String,
    @Value("\${app.gemini.model:gemini-2.5-flash-lite}") private val model: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = WebClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com")
        .build()

    fun generateText(prompt: String): String? {
        if (apiKey.isBlank()) {
            log.warn("Gemini API key not configured — set GEMINI_API_KEY")
            return null
        }
        return try {
            val body = mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to prompt)))
                )
            )
            @Suppress("UNCHECKED_CAST")
            val response = webClient.post()
                .uri("/v1beta/models/$model:generateContent?key=$apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as Map<String, Any>?

            val candidates = response?.get("candidates") as? List<Map<String, Any>>
            val content   = candidates?.firstOrNull()?.get("content") as? Map<String, Any>
            val parts     = content?.get("parts") as? List<Map<String, Any>>
            parts?.firstOrNull()?.get("text") as? String
        } catch (e: WebClientResponseException) {
            log.error("Gemini API error: ${e.statusCode} — ${e.responseBodyAsString}")
            null
        } catch (e: Exception) {
            log.error("Gemini unexpected error: ${e.message}", e)
            null
        }
    }
}
