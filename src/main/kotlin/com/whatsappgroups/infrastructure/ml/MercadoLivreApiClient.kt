package com.whatsappgroups.infrastructure.ml

import com.fasterxml.jackson.annotation.JsonProperty
import com.whatsappgroups.application.dto.MlItemDetails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.net.URI

@Component
class MercadoLivreApiClient(
    @Value("\${app.mercadolivre.client-id:}")
    private val clientId: String,
    @Value("\${app.mercadolivre.client-secret:}")
    private val clientSecret: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val webClient = WebClient.builder()
        .baseUrl("https://api.mercadolibre.com")
        .build()

    // WebClient sem follow-redirect para seguir a cadeia manualmente
    private val noRedirectClient = WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
        .build()

    private val pageClient = WebClient.builder()
        .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
        .build()

    fun validateToken(accessToken: String): Boolean {
        return try {
            val valid = webClient.get()
                .uri("/users/me")
                .header("Authorization", "Bearer $accessToken")
                .exchangeToMono { response ->
                    response.releaseBody().thenReturn(response.statusCode() == HttpStatus.OK)
                }
                .block() ?: false
            log.debug("Token validation result: $valid")
            valid
        } catch (e: Exception) {
            log.warn("Token validation failed: ${e.message}")
            false
        }
    }

    // Items API is public — no auth required for published items
    fun getItem(itemId: String): MlItemDetails? {
        return try {
            val item = webClient.get()
                .uri("/items/{itemId}", itemId)
                .retrieve()
                .bodyToMono<MlItemDetails>()
                .block()
            log.info("Fetched item $itemId: ${item?.title}")
            item
        } catch (e: Exception) {
            log.error("Error fetching item $itemId: ${e.message}", e)
            null
        }
    }

    fun resolveShortLink(meliUrl: String): String {
        var current = meliUrl
        for (hop in 1..5) {
            val location = try {
                noRedirectClient.get()
                    .uri(current)
                    .exchangeToMono { response ->
                        val loc = response.headers().asHttpHeaders().location?.toString()
                        response.releaseBody().thenReturn(loc ?: "")
                    }
                    .block()
            } catch (e: Exception) {
                log.error("Error following redirect from $current: ${e.message}", e)
                break
            }

            if (location.isNullOrBlank()) break

            // resolve relative redirects
            current = if (location.startsWith("http")) location
                      else URI(current).resolve(location).toString()
        }

        log.info("Resolved meli.la link: $meliUrl → $current")
        return current
    }

    fun extractItemIdFromPage(url: String): String? {
        return try {
            val body = pageClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/json")
                .retrieve()
                .bodyToMono<String>()
                .block()

            // ML embeds product_id in JSON polycards metadata inside the page body
            val pattern = Regex("""product_id["\s:]+((MLB|MLA|MLM|MLC|MCO|MPE|MLU|MLV)\d+)""")
            val match = pattern.find(body ?: "")
            if (match != null) {
                log.info("Extracted product_id from page body at $url: ${match.groupValues[1]}")
                match.groupValues[1]
            } else {
                null
            }
        } catch (e: Exception) {
            log.error("Error extracting product_id from page at $url: ${e.message}", e)
            null
        }
    }

    fun generateAffiliateLink(accessToken: String, itemId: String): String {
        return try {
            val response = webClient.get()
                .uri("/affiliate_program/link?item_id={itemId}", itemId)
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()

            val url = response?.get("url") as? String
            if (url != null) {
                log.info("Generated affiliate link for item $itemId: $url")
                url
            } else {
                log.warn("No URL returned from affiliate_program/link for item $itemId")
                throw IllegalStateException("No affiliate link URL returned")
            }
        } catch (e: Exception) {
            log.error("Error generating affiliate link for item $itemId: ${e.message}", e)
            throw e
        }
    }

    fun exchangeCodeForToken(code: String, redirectUri: String): MlTokenResponse {
        return try {
            val response = webClient.post()
                .uri("/oauth/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=authorization_code&code=$code&redirect_uri=$redirectUri&client_id=$clientId&client_secret=$clientSecret")
                .retrieve()
                .bodyToMono<MlTokenResponse>()
                .block()

            if (response != null) {
                log.info("Successfully exchanged authorization code for token")
                response
            } else {
                throw IllegalStateException("No token response from ML")
            }
        } catch (e: Exception) {
            log.error("Error exchanging code for token: ${e.message}", e)
            throw e
        }
    }

    fun refreshToken(refreshToken: String): MlTokenResponse {
        return try {
            val response = webClient.post()
                .uri("/oauth/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=refresh_token&refresh_token=$refreshToken&client_id=$clientId&client_secret=$clientSecret")
                .retrieve()
                .bodyToMono<MlTokenResponse>()
                .block()

            if (response != null) {
                log.info("Successfully refreshed access token")
                response
            } else {
                throw IllegalStateException("No token response from ML refresh")
            }
        } catch (e: Exception) {
            log.error("Error refreshing token: ${e.message}", e)
            throw e
        }
    }

    fun getMe(accessToken: String): MlUserInfo {
        return try {
            val response = webClient.get()
                .uri("/users/me?access_token={token}", accessToken)
                .retrieve()
                .bodyToMono<MlUserInfo>()
                .block()

            if (response != null) {
                log.info("Retrieved ML user info: ${response.nickname}")
                response
            } else {
                throw IllegalStateException("No user info response from ML")
            }
        } catch (e: Exception) {
            log.error("Error fetching ML user info: ${e.message}", e)
            throw e
        }
    }
}

data class MlTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("refresh_token")
    val refreshToken: String? = null,
    @JsonProperty("expires_in")
    val expiresIn: Long? = null,
    @JsonProperty("token_type")
    val tokenType: String = "Bearer"
)

data class MlUserInfo(
    val id: String,
    val nickname: String
)
