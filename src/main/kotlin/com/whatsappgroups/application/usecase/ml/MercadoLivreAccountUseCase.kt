package com.whatsappgroups.application.usecase.ml

import com.whatsappgroups.domain.model.MercadoLivreAccount
import com.whatsappgroups.domain.repository.MercadoLivreAccountRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val meliLinkPattern = Regex("https?://meli\\.la/\\S+")

    fun getOAuthUrl(userId: UUID): String {
        val state = UUID.randomUUID().toString()
        val stateKey = "ml:state:$state"
        redisTemplate.opsForValue().set(stateKey, userId.toString(), 10, TimeUnit.MINUTES)

        val encodedRedirectUri = URLEncoder.encode(mlRedirectUri, StandardCharsets.UTF_8)

        return "https://auth.mercadolivre.com.br/authorization?" +
                "response_type=code&" +
                "client_id=$mlClientId&" +
                "redirect_uri=$encodedRedirectUri&" +
                "state=$state"
    }

    @Transactional
    fun handleCallback(code: String, state: String): MercadoLivreAccount {
        val stateKey = "ml:state:$state"
        val userIdStr = redisTemplate.opsForValue().get(stateKey)
            ?: throw IllegalStateException("Invalid or expired state parameter")

        val userId = UUID.fromString(userIdStr)
        redisTemplate.delete(stateKey)

        val tokenResponse = mlApiClient.exchangeCodeForToken(code, mlRedirectUri)
        val userInfo = mlApiClient.getMe(tokenResponse.accessToken)

        val user = userRepository.getReferenceById(userId)
        val existingAccount = mlAccountRepository.findByOwner(user).orElse(null)

        val expiresAt = if (tokenResponse.expiresIn != null) {
            LocalDateTime.now().plusSeconds(tokenResponse.expiresIn)
        } else {
            null
        }

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

        log.info("ML account connected for user $userId: ${userInfo.nickname}")
        return mlAccountRepository.save(account)
    }

    fun getStatus(userId: UUID): Pair<Boolean, String?> {
        val user = userRepository.getReferenceById(userId)
        val account = mlAccountRepository.findByOwner(user).orElse(null)
        return if (account != null) {
            Pair(true, account.mlNickname)
        } else {
            Pair(false, null)
        }
    }

    @Transactional
    fun disconnect(userId: UUID) {
        val user = userRepository.getReferenceById(userId)
        val account = mlAccountRepository.findByOwner(user).orElse(null)
        if (account != null) {
            mlAccountRepository.delete(account)
            log.info("ML account disconnected for user $userId")
        }
    }

    fun resolveAndReplaceLinks(text: String, account: MercadoLivreAccount): String {
        var result = text
        val links = meliLinkPattern.findAll(text)

        for (match in links) {
            val meliUrl = match.value
            try {
                val itemId = resolveLinkWithCache(meliUrl)
                if (itemId != null) {
                    val affiliateUrl = generateAffiliateLinkWithCache(account.owner.id!!, itemId, account.accessToken)
                    if (affiliateUrl != null) {
                        result = result.replace(meliUrl, affiliateUrl)
                        log.debug("Replaced $meliUrl with affiliate link")
                    }
                }
            } catch (e: Exception) {
                log.error("Error processing link $meliUrl: ${e.message}", e)
            }
        }

        return result
    }

    private fun resolveLinkWithCache(meliUrl: String): String? {
        val hash = sha256(meliUrl)
        val cacheKey = "ml:item:$hash"

        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            log.debug("Cache hit for item resolution: $meliUrl")
            return cached
        }

        return try {
            val resolvedUrl = mlApiClient.resolveShortLink(meliUrl)
            val itemId = extractItemId(resolvedUrl)
            if (itemId != null) {
                redisTemplate.opsForValue().set(cacheKey, itemId, 7, TimeUnit.DAYS)
            }
            itemId
        } catch (e: Exception) {
            log.error("Error resolving link $meliUrl: ${e.message}", e)
            null
        }
    }

    private fun generateAffiliateLinkWithCache(userId: UUID, itemId: String, accessToken: String): String? {
        val cacheKey = "ml:aff:$userId:$itemId"

        val cached = redisTemplate.opsForValue().get(cacheKey)
        if (cached != null) {
            log.debug("Cache hit for affiliate link: $itemId")
            return cached
        }

        return try {
            val affiliateUrl = mlApiClient.generateAffiliateLink(accessToken, itemId)
            redisTemplate.opsForValue().set(cacheKey, affiliateUrl, 24, TimeUnit.HOURS)
            affiliateUrl
        } catch (e: Exception) {
            log.error("Error generating affiliate link for $itemId: ${e.message}", e)
            null
        }
    }

    private fun extractItemId(url: String): String? {
        val pattern = Regex("(MLB|MLA|MLM|MLC|MCO|MPE|MLU|MLV)[-]?(\\d+)")
        val match = pattern.find(url)
        return if (match != null) {
            match.groupValues[1] + match.groupValues[2]
        } else {
            log.warn("Could not extract item ID from URL: $url")
            null
        }
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
