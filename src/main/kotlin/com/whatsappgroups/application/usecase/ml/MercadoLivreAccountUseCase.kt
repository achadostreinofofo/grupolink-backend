package com.whatsappgroups.application.usecase.ml

import com.whatsappgroups.application.dto.MlAffiliateParamsRequest
import com.whatsappgroups.application.dto.MlStatusResponse
import com.whatsappgroups.application.usecase.shortlink.CreateShortLinkRequest
import com.whatsappgroups.application.usecase.shortlink.ShortLinkUseCase
import com.whatsappgroups.domain.model.MercadoLivreAccount
import com.whatsappgroups.domain.repository.MercadoLivreAccountRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class MercadoLivreAccountUseCase(
    private val mlAccountRepository: MercadoLivreAccountRepository,
    private val userRepository: UserRepository,
    private val mlApiClient: MercadoLivreApiClient,
    private val shortLinkUseCase: ShortLinkUseCase,
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${app.mercadolivre.client-id:}")
    private val mlClientId: String,
    @Value("\${app.mercadolivre.redirect-uri:http://localhost:8080/api/ml/oauth/callback}")
    private val mlRedirectUri: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val meliLinkPattern = Regex("""https?://meli\.la/\S+""")

    fun getOAuthUrl(userId: UUID): String {
        val state = UUID.randomUUID().toString()
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        redisTemplate.opsForValue().set("ml:state:$state", userId.toString(), 10, TimeUnit.MINUTES)
        redisTemplate.opsForValue().set("ml:pkce:$state", codeVerifier, 10, TimeUnit.MINUTES)
        val encodedRedirectUri = URLEncoder.encode(mlRedirectUri, StandardCharsets.UTF_8)
        return "https://auth.mercadolivre.com.br/authorization?" +
                "response_type=code&client_id=$mlClientId&redirect_uri=$encodedRedirectUri&state=$state" +
                "&code_challenge=$codeChallenge&code_challenge_method=S256"
    }

    @Transactional
    fun handleCallback(code: String, state: String): MercadoLivreAccount {
        val userIdStr = redisTemplate.opsForValue().get("ml:state:$state")
            ?: throw IllegalStateException("Invalid or expired state parameter")
        val userId = UUID.fromString(userIdStr)
        val codeVerifier = redisTemplate.opsForValue().get("ml:pkce:$state")
            ?: throw IllegalStateException("Invalid or expired PKCE state")
        redisTemplate.delete("ml:state:$state")
        redisTemplate.delete("ml:pkce:$state")

        val tokenResponse = mlApiClient.exchangeCodeForToken(code, mlRedirectUri, codeVerifier)
        val userInfo = mlApiClient.getMe(tokenResponse.accessToken)
        val user = userRepository.getReferenceById(userId)
        val existingAccount = mlAccountRepository.findByOwner(user).orElse(null)

        val expiresAt = tokenResponse.expiresIn?.let { LocalDateTime.now().plusSeconds(it) }

        val account = if (existingAccount != null) {
            existingAccount.apply {
                this.accessToken = tokenResponse.accessToken
                this.refreshToken = tokenResponse.refreshToken
                this.tokenExpiresAt = expiresAt
                this.updatedAt = LocalDateTime.now()
            }
        } else {
            MercadoLivreAccount(
                owner = user,
                mlUserId = userInfo.id,
                mlNickname = userInfo.nickname,
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                tokenExpiresAt = expiresAt
            )
        }

        val saved = mlAccountRepository.save(account)
        redisTemplate.delete("ml:tokenvalid:$userId")

        // Affiliate params (matt_word / matt_tool) are provided by the user via saveAffiliateParams.
        // The ML public API has no endpoint to generate affiliate links, so there is nothing to
        // auto-capture here.
        log.info("ML account connected for user $userId: ${userInfo.nickname}")
        return saved
    }

    @Transactional
    fun saveAffiliateParams(userId: UUID, request: MlAffiliateParamsRequest) {
        val user = userRepository.getReferenceById(userId)
        val account = mlAccountRepository.findByOwner(user).orElse(null)
            ?: throw IllegalStateException("No ML account found for user $userId")
        account.mattWord = request.mattWord
        account.mattTool = request.mattTool
        account.updatedAt = LocalDateTime.now()
        mlAccountRepository.save(account)
        log.info("Affiliate params saved for user $userId: matt_word=${request.mattWord}, matt_tool=${request.mattTool}")
    }

    @Transactional
    fun getStatus(userId: UUID): MlStatusResponse {
        val user = userRepository.getReferenceById(userId)
        val account = mlAccountRepository.findByOwner(user).orElse(null)
            ?: return MlStatusResponse(connected = false, nickname = null)

        val affiliateConfigured = !account.mattWord.isNullOrBlank() && !account.mattTool.isNullOrBlank()

        val cacheKey = "ml:tokenvalid:$userId"
        if (redisTemplate.opsForValue().get(cacheKey) == "valid") {
            return MlStatusResponse(
                connected = true,
                nickname = account.mlNickname,
                tokenValid = true,
                affiliateConfigured = affiliateConfigured
            )
        }

        if (mlApiClient.validateToken(account.accessToken)) {
            redisTemplate.opsForValue().set(cacheKey, "valid", 5, TimeUnit.MINUTES)
            return MlStatusResponse(
                connected = true,
                nickname = account.mlNickname,
                tokenValid = true,
                affiliateConfigured = affiliateConfigured
            )
        }

        val refreshToken = account.refreshToken
            ?: return MlStatusResponse(
                connected = true, nickname = account.mlNickname,
                tokenValid = false, tokenExpired = true,
                affiliateConfigured = affiliateConfigured,
                error = "Token expirado. Reconecte a integração com o Mercado Livre."
            )

        return try {
            val tokenResponse = mlApiClient.refreshToken(refreshToken)
            account.accessToken = tokenResponse.accessToken
            if (tokenResponse.refreshToken != null) account.refreshToken = tokenResponse.refreshToken
            account.tokenExpiresAt = tokenResponse.expiresIn?.let { LocalDateTime.now().plusSeconds(it) }
            account.updatedAt = LocalDateTime.now()
            mlAccountRepository.save(account)
            redisTemplate.opsForValue().set(cacheKey, "valid", 5, TimeUnit.MINUTES)

            MlStatusResponse(
                connected = true, nickname = account.mlNickname, tokenValid = true,
                affiliateConfigured = !account.mattWord.isNullOrBlank() && !account.mattTool.isNullOrBlank()
            )
        } catch (e: Exception) {
            MlStatusResponse(
                connected = true, nickname = account.mlNickname,
                tokenValid = false, tokenExpired = true,
                affiliateConfigured = affiliateConfigured,
                error = "Token expirado e não foi possível renovar. Reconecte a integração."
            )
        }
    }

    @Transactional
    fun disconnect(userId: UUID) {
        val user = userRepository.getReferenceById(userId)
        mlAccountRepository.findByOwner(user).orElse(null)?.let {
            mlAccountRepository.delete(it)
            redisTemplate.delete("ml:tokenvalid:$userId")
            log.info("ML account disconnected for user $userId")
        }
    }

    fun resolveAndReplaceLinks(text: String, account: MercadoLivreAccount): String {
        val mattWord = account.mattWord
        val mattTool = account.mattTool
        if (mattWord.isNullOrBlank() || mattTool.isNullOrBlank()) {
            log.warn("Affiliate params not configured for user ${account.owner.id} — skipping link substitution")
            return text
        }

        // Hibernate proxy always exposes the id without triggering lazy load
        val ownerId = account.owner.id ?: return text

        var result = text
        for (match in meliLinkPattern.findAll(text)) {
            val meliUrl = match.value
            try {
                val resolvedUrl = resolveLinkWithCache(meliUrl) ?: continue

                // When the short link resolves straight to a product page the MLB id is in the URL.
                // For affiliate "social" pages the id has to be extracted from the rendered HTML.
                val directMlbId = extractMlbId(resolvedUrl)
                val mlbId = directMlbId
                    ?: if (resolvedUrl.contains("/social/")) extractMlbIdFromSocialWithCache(resolvedUrl) else null
                if (mlbId == null) {
                    log.warn("Nenhum MLB ID encontrado em $resolvedUrl — link mantido sem alteração")
                    continue
                }

                // Canonical product URL: reuse the resolved URL when it already points to the product,
                // otherwise look up the permalink via the items API (falling back to a built URL).
                val baseProductUrl = if (directMlbId != null && !resolvedUrl.contains("/social/")) {
                    resolvedUrl
                } else {
                    resolvePermalinkWithCache(mlbId, account.accessToken)
                        ?: "https://www.mercadolivre.com.br/p/$mlbId"
                }

                // Attribution is done purely through the matt_word / matt_tool query params of the
                // logged-in user. ML has no public API to mint affiliate links.
                val affiliateUrl = appendAffiliateParams(baseProductUrl, mattWord, mattTool)
                val shortUrl = shortenWithCache(ownerId, affiliateUrl)
                result = result.replace(meliUrl, shortUrl)
                log.info("Substituted affiliate link: $meliUrl → $shortUrl (matt_word=$mattWord)")
            } catch (e: Exception) {
                log.error("Error processing link $meliUrl: ${e.message}", e)
            }
        }
        return result
    }

    // Removes the sharer's affiliate/tracking params and appends the logged-in user's own ones,
    // so the redistributed link attributes the sale to the current account.
    private fun appendAffiliateParams(url: String, mattWord: String, mattTool: String): String {
        val base = stripAffiliateParams(url)
        val sep = if (base.contains("?")) "&" else "?"
        val w = URLEncoder.encode(mattWord, StandardCharsets.UTF_8)
        val t = URLEncoder.encode(mattTool, StandardCharsets.UTF_8)
        return "$base${sep}matt_word=$w&matt_tool=$t"
    }

    private val affiliateParamKeys = setOf(
        "matt_word", "matt_tool", "ref", "forceInApp", "matt_product_id", "tracking_id"
    )

    private fun stripAffiliateParams(url: String): String {
        val qIdx = url.indexOf('?')
        if (qIdx < 0) return url
        val path = url.substring(0, qIdx)
        val kept = url.substring(qIdx + 1)
            .split("&")
            .filter { it.isNotBlank() && it.substringBefore('=') !in affiliateParamKeys }
        return if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
    }

    // Looks up the canonical product permalink and caches it per product for 7 days.
    private fun resolvePermalinkWithCache(mlbId: String, accessToken: String): String? {
        val cacheKey = "ml:permalink:$mlbId"
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) return cached.takeIf { it.isNotBlank() }

        val permalink = mlApiClient.getItem(mlbId, accessToken)?.permalink
        redisTemplate.opsForValue().set(cacheKey, permalink ?: "", 7, TimeUnit.DAYS)
        if (permalink == null) log.warn("Items API não retornou permalink para $mlbId")
        return permalink
    }

    private fun shortenWithCache(ownerId: UUID, affiliateUrl: String): String {
        val cacheKey = "ml:shorturl:${sha256(affiliateUrl)}"
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) return cached

        val response = shortLinkUseCase.create(ownerId, CreateShortLinkRequest(targetUrl = affiliateUrl))
        val shortUrl = response.shortUrl

        // Only cache in Redis after the outer transaction commits.
        // If the transaction rolls back, the short_link row is gone from the DB but
        // Redis would hold a stale URL for 30 days, causing every click to 404.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    redisTemplate.opsForValue().set(cacheKey, shortUrl, 30, TimeUnit.DAYS)
                }
            })
        } else {
            redisTemplate.opsForValue().set(cacheKey, shortUrl, 30, TimeUnit.DAYS)
        }

        return shortUrl
    }

    // Fetches HTML of a social profile page to extract the featured product's MLB ID.
    // Cached for 24 hours since the same social URL always points to the same product.
    private fun extractMlbIdFromSocialWithCache(socialUrl: String): String? {
        val cacheKey = "ml:social-mlbid:${sha256(socialUrl)}"
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            return cached.takeIf { it.isNotBlank() }
        }
        val mlbId = mlApiClient.extractMlbIdFromPageHtml(socialUrl)
        redisTemplate.opsForValue().set(cacheKey, mlbId ?: "", 24, TimeUnit.HOURS)
        log.info("MLB ID extraído do HTML de $socialUrl → $mlbId")
        return mlbId
    }

    // Extracts MLB item ID from any ML URL (e.g. MLB-2018088093 or MLB27844396).
    private fun extractMlbId(url: String): String? {
        val match = Regex("""MLB-?(\d+)""").find(url) ?: return null
        return "MLB${match.groupValues[1]}"
    }

    // Caches the resolved meli.la URL for 7 days to avoid repeated HTTP requests.
    private fun resolveLinkWithCache(meliUrl: String): String? {
        val cacheKey = "ml:resolved:${sha256(meliUrl)}"
        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) return cached

        return try {
            val resolved = mlApiClient.resolveShortLink(meliUrl)
            redisTemplate.opsForValue().set(cacheKey, resolved, 7, TimeUnit.DAYS)
            resolved
        } catch (e: Exception) {
            log.error("Error resolving link $meliUrl: ${e.message}", e)
            null
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
