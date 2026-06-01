package com.whatsappgroups.infrastructure.ml

import com.fasterxml.jackson.annotation.JsonProperty
import com.whatsappgroups.application.dto.MlItemDetails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

    private val noRedirectClient = WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(HttpClient.create().followRedirect(false)))
        .build()

    fun validateToken(accessToken: String): Boolean {
        return try {
            webClient.get()
                .uri("/users/me")
                .header("Authorization", "Bearer $accessToken")
                .exchangeToMono { response ->
                    response.releaseBody().thenReturn(response.statusCode() == HttpStatus.OK)
                }
                .block() ?: false
        } catch (e: Exception) {
            log.warn("Token validation failed: ${e.message}")
            false
        }
    }

    // Calls /affiliate_program/link for a specific item and returns the affiliate URL.
    // Returns null if the user is not in the affiliate program or the item is not found.
    fun getAffiliateLinkForItem(accessToken: String, mlbId: String): String? {
        return try {
            val response = webClient.get()
                .uri("/affiliate_program/link?item_id={id}", mlbId)
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .bodyToMono<Map<String, Any>>()
                .block()
            val url = response?.get("url") as? String
            if (url == null) log.warn("getAffiliateLinkForItem: sem campo 'url' para $mlbId — body: $response")
            url
        } catch (e: WebClientResponseException) {
            log.warn("getAffiliateLinkForItem falhou [$mlbId]: ${e.statusCode} - ${e.responseBodyAsString}")
            null
        } catch (e: Exception) {
            log.warn("getAffiliateLinkForItem falhou [$mlbId]: ${e.message}")
            null
        }
    }

    // Items API is public — no auth required for published items
    fun getItem(itemId: String): MlItemDetails? {
        return try {
            webClient.get()
                .uri("/items/{itemId}", itemId)
                .retrieve()
                .bodyToMono<MlItemDetails>()
                .block()
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

            current = if (location.startsWith("http")) location
                      else URI(current).resolve(location).toString()
        }

        log.info("Resolved meli.la link: $meliUrl → $current")
        return current
    }

    // Follows redirects from any ML/meli.la URL and returns the first (matt_word, matt_tool)
    // pair found in any hop. Returns (null, null) if not found after 5 hops.
    fun resolveAffiliateParams(startUrl: String): Pair<String?, String?> {
        var current = startUrl
        for (hop in 0..5) {
            val (w, t) = extractAffiliateParamsFromUrl(current)
            if (w != null && t != null) {
                log.info("Found affiliate params at hop $hop: $current")
                return w to t
            }

            val next = try {
                noRedirectClient.get()
                    .uri(current)
                    .exchangeToMono { response ->
                        val loc = response.headers().asHttpHeaders().location?.toString()
                        response.releaseBody().thenReturn(loc ?: "")
                    }
                    .block()
            } catch (e: Exception) {
                log.warn("Error resolving affiliate params from $current: ${e.message}")
                break
            }

            if (next.isNullOrBlank()) break
            current = if (next.startsWith("http")) next else URI(current).resolve(next).toString()
        }
        log.warn("No affiliate params found after resolving $startUrl → $current")
        return null to null
    }

    private fun extractAffiliateParamsFromUrl(url: String): Pair<String?, String?> {
        return try {
            val uri = URI(url)
            val queryParams = uri.query?.split("&")?.associate {
                val i = it.indexOf('=')
                if (i > 0) it.substring(0, i) to it.substring(i + 1) else it to ""
            } ?: emptyMap()
            // matt_word can be a query param OR embedded in the path as /social/<value>
            val mattWord = queryParams["matt_word"]
                ?: Regex("""/social/([^/?&#]+)""").find(uri.path ?: "")?.groupValues?.get(1)
            val mattTool = queryParams["matt_tool"]
            mattWord to mattTool
        } catch (_: Exception) {
            null to null
        }
    }

    fun exchangeCodeForToken(code: String, redirectUri: String, codeVerifier: String): MlTokenResponse {
        val encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        return try {
            val response = webClient.post()
                .uri("/oauth/token")
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=authorization_code&code=$code&redirect_uri=$encodedRedirectUri&client_id=$clientId&client_secret=$clientSecret&code_verifier=$codeVerifier")
                .retrieve()
                .bodyToMono<MlTokenResponse>()
                .block() ?: throw IllegalStateException("No token response from ML")
            log.info("Successfully exchanged authorization code for token")
            response
        } catch (e: WebClientResponseException) {
            log.error("ML token exchange failed: ${e.statusCode} - ${e.responseBodyAsString}")
            throw e
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
                .block() ?: throw IllegalStateException("No token response from ML refresh")
            log.info("Successfully refreshed access token")
            response
        } catch (e: WebClientResponseException) {
            log.error("ML token refresh failed: ${e.statusCode} - ${e.responseBodyAsString}")
            throw e
        } catch (e: Exception) {
            log.error("Error refreshing token: ${e.message}", e)
            throw e
        }
    }

    fun getMe(accessToken: String): MlUserInfo {
        return try {
            webClient.get()
                .uri("/users/me?access_token={token}", accessToken)
                .retrieve()
                .bodyToMono<MlUserInfo>()
                .block() ?: throw IllegalStateException("No user info response from ML")
        } catch (e: Exception) {
            log.error("Error fetching ML user info: ${e.message}", e)
            throw e
        }
    }
}

data class MlTokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("refresh_token") val refreshToken: String? = null,
    @JsonProperty("expires_in") val expiresIn: Long? = null,
    @JsonProperty("token_type") val tokenType: String = "Bearer"
)

data class MlUserInfo(
    val id: String,
    val nickname: String
)
