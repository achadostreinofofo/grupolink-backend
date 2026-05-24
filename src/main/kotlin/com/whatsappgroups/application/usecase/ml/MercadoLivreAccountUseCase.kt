package com.whatsappgroups.application.usecase.ml

import com.whatsappgroups.application.dto.MlAffiliateParamsRequest
import com.whatsappgroups.application.dto.MlStatusResponse
import com.whatsappgroups.domain.model.MercadoLivreAccount
import com.whatsappgroups.domain.repository.MercadoLivreAccountRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class MercadoLivreAccountUseCase(
    private val mlAccountRepository: MercadoLivreAccountRepository,
    private val userRepository: UserRepository,
    private val mlApiClient: MercadoLivreApiClient,
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
        redisTemplate.opsForValue().set("ml:state:$state", userId.toString(), 10, TimeUnit.MINUTES)
        val encodedRedirectUri = URLEncoder.encode(mlRedirectUri, StandardCharsets.UTF_8)
        return "https://auth.mercadolivre.com.br/authorization?" +
                "response_type=code&client_id=$mlClientId&redirect_uri=$encodedRedirectUri&state=$state"
    }

    @Transactional
    fun handleCallback(code: String, state: String): MercadoLivreAccount {
        val userIdStr = redisTemplate.opsForValue().get("ml:state:$state")
            ?: throw IllegalStateException("Invalid or expired state parameter")
        val userId = UUID.fromString(userIdStr)
        redisTemplate.delete("ml:state:$state")

        val tokenResponse = mlApiClient.exchangeCodeForToken(code, mlRedirectUri)
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

        // Auto-capture affiliate params (matt_word / matt_tool) from the affiliate program API.
        // This is best-effort: if the user is not registered in the ML affiliate program, it is skipped.
        tryAutoCapturAffiliateParams(saved, userId)

        log.info("ML account connected for user $userId: ${userInfo.nickname}")
        return saved
    }

    private fun tryAutoCapturAffiliateParams(account: MercadoLivreAccount, userId: UUID) {
        try {
            val sampleUrl = mlApiClient.fetchSampleAffiliateLink(account.accessToken) ?: return
            val (mattWord, mattTool) = parseAffiliateParams(sampleUrl)
            if (mattWord != null && mattTool != null) {
                account.mattWord = mattWord
                account.mattTool = mattTool
                account.updatedAt = LocalDateTime.now()
                mlAccountRepository.save(account)
                log.info("Auto-captured affiliate params for user $userId: matt_word=$mattWord, matt_tool=$mattTool")
            }
        } catch (e: Exception) {
            log.warn("Failed to auto-capture affiliate params for user $userId: ${e.message}")
        }
    }

    private fun parseAffiliateParams(affiliateUrl: String): Pair<String?, String?> {
        return try {
            val query = URI(affiliateUrl).query ?: return null to null
            val params = query.split("&").associate {
                val idx = it.indexOf('=')
                if (idx > 0) it.substring(0, idx) to it.substring(idx + 1) else it to ""
            }
            params["matt_word"] to params["matt_tool"]
        } catch (e: Exception) {
            null to null
        }
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

            // If affiliate params were not captured before, try again with the refreshed token
            if (!affiliateConfigured) tryAutoCapturAffiliateParams(account, userId)

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

        var result = text
        for (match in meliLinkPattern.findAll(text)) {
            val meliUrl = match.value
            try {
                val resolvedUrl = resolveLinkWithCache(meliUrl) ?: continue
                val affiliateUrl = buildAffiliateUrl(resolvedUrl, mattWord, mattTool)
                result = result.replace(meliUrl, affiliateUrl)
                log.info("Substituted affiliate link: $meliUrl → $affiliateUrl")
            } catch (e: Exception) {
                log.error("Error processing link $meliUrl: ${e.message}", e)
            }
        }
        return result
    }

    // Replaces or adds matt_word and matt_tool, preserving all other URL parameters.
    private fun buildAffiliateUrl(resolvedUrl: String, mattWord: String, mattTool: String): String {
        return try {
            val uri = URI(resolvedUrl)
            val existingParams = uri.query
                ?.split("&")
                ?.filter { param ->
                    val key = param.substringBefore("=")
                    key != "matt_word" && key != "matt_tool" && key != "tag"
                }
                ?.joinToString("&")
                ?: ""

            val newQuery = buildString {
                if (existingParams.isNotEmpty()) append(existingParams).append("&")
                append("matt_word=").append(mattWord)
                append("&matt_tool=").append(mattTool)
            }

            URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, newQuery, uri.fragment).toString()
        } catch (e: Exception) {
            log.error("Error building affiliate URL for $resolvedUrl: ${e.message}", e)
            resolvedUrl
        }
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

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
