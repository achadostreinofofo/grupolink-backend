package com.whatsappgroups.infrastructure.ml

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient

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

    // WebClient sem follow-redirect para capturar o Location header do meli.la
    private val noRedirectClient = WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
        .build()

    fun resolveShortLink(meliUrl: String): String {
        return try {
            // meli.la retorna redirect somente em GET, não em HEAD
            val resolved = noRedirectClient.get()
                .uri(meliUrl)
                .exchangeToMono { response ->
                    val location = response.headers().asHttpHeaders().location?.toString()
                    response.releaseBody().thenReturn(location ?: meliUrl)
                }
                .block() ?: meliUrl

            log.info("Resolved meli.la link: $meliUrl → $resolved")
            resolved
        } catch (e: Exception) {
            log.error("Error resolving short link $meliUrl: ${e.message}", e)
            throw e
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
