@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.MlItemDetails
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.application.usecase.shortlink.ShortLinkResponse
import com.whatsappgroups.application.usecase.shortlink.ShortLinkUseCase
import com.whatsappgroups.domain.model.MercadoLivreAccount
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.repository.MercadoLivreAccountRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import com.whatsappgroups.infrastructure.ml.MlTokenResponse
import com.whatsappgroups.infrastructure.ml.MlUserInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.Optional
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MercadoLivreAccountUseCaseTest {

    @Mock private lateinit var mlAccountRepository: MercadoLivreAccountRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var mlApiClient: MercadoLivreApiClient
    @Mock private lateinit var shortLinkUseCase: ShortLinkUseCase
    @Mock private lateinit var redisTemplate: RedisTemplate<String, String>
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var useCase: MercadoLivreAccountUseCase
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        useCase = MercadoLivreAccountUseCase(
            mlAccountRepository, userRepository, mlApiClient, shortLinkUseCase, redisTemplate,
            mlClientId = "test-client-id", mlRedirectUri = "http://localhost:8080/api/ml/oauth/callback"
        )
    }

    // ── OAuth ──────────────────────────────────────────────────────────────────

    @Test
    fun `getOAuthUrl stores state and returns ML authorization URL`() {
        val keyCaptor = argumentCaptor<String>()
        val url = useCase.getOAuthUrl(userId)

        assertThat(url).contains("auth.mercadolivre.com.br/authorization")
        assertThat(url).contains("client_id=test-client-id")
        assertThat(url).contains("code_challenge=")
        assertThat(url).contains("code_challenge_method=S256")
        verify(valueOps).set(keyCaptor.capture(), eq(userId.toString()), eq(10L), eq(TimeUnit.MINUTES))
        assertThat(keyCaptor.firstValue).startsWith("ml:state:")
    }

    @Test
    fun `handleCallback throws on invalid state`() {
        whenever(valueOps.get("ml:state:bad")).thenReturn(null)
        assertThatThrownBy { useCase.handleCallback("code", "bad") }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `handleCallback creates new ML account`() {
        val state = "valid-state"
        val owner = user()
        val saved = mlAccount(owner, "access-token-123")
        whenever(valueOps.get("ml:state:$state")).thenReturn(userId.toString())
        whenever(valueOps.get("ml:pkce:$state")).thenReturn("test-code-verifier")
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlApiClient.exchangeCodeForToken(any(), any(), any()))
            .thenReturn(MlTokenResponse(accessToken = "access-token-123", refreshToken = "refresh", expiresIn = 21600L))
        whenever(mlApiClient.getMe("access-token-123")).thenReturn(MlUserInfo(id = "ml-999", nickname = "TestSeller"))
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.empty())
        whenever(mlAccountRepository.save(any()) as MercadoLivreAccount?).thenReturn(saved)

        val account = useCase.handleCallback("auth-code", state)

        assertThat(account.accessToken).isEqualTo("access-token-123")
        verify(redisTemplate).delete("ml:state:$state")
    }

    @Test
    fun `handleCallback updates existing account`() {
        val state = "state-2"
        val owner = user()
        val existing = mlAccount(owner, "old-token")
        whenever(valueOps.get("ml:state:$state")).thenReturn(userId.toString())
        whenever(valueOps.get("ml:pkce:$state")).thenReturn("test-code-verifier")
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlApiClient.exchangeCodeForToken(any(), any(), any()))
            .thenReturn(MlTokenResponse(accessToken = "new-token"))
        whenever(mlApiClient.getMe("new-token")).thenReturn(MlUserInfo(id = "ml-999", nickname = "TestSeller"))
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(existing))
        whenever(mlAccountRepository.save(any()) as MercadoLivreAccount?).thenReturn(existing)

        val account = useCase.handleCallback("auth-code", state)
        assertThat(account.accessToken).isEqualTo("new-token")
    }

    // ── Status ─────────────────────────────────────────────────────────────────

    @Test
    fun `getStatus returns connected false when no account`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.empty())

        val status = useCase.getStatus(userId)
        assertThat(status.connected).isFalse()
    }

    @Test
    fun `getStatus returns tokenValid true on cache hit`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(mlAccount(owner)))
        whenever(valueOps.get("ml:tokenvalid:$userId")).thenReturn("valid")

        val status = useCase.getStatus(userId)
        assertThat(status.connected).isTrue()
        assertThat(status.tokenValid).isTrue()
        assertThat(status.nickname).isEqualTo("TestSeller")
        verify(mlApiClient, never()).validateToken(any())
    }

    @Test
    fun `getStatus validates token live on cache miss`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(mlAccount(owner)))
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.validateToken("test-access-token")).thenReturn(true)

        val status = useCase.getStatus(userId)
        assertThat(status.connected).isTrue()
        assertThat(status.tokenValid).isTrue()
        verify(mlApiClient).validateToken("test-access-token")
    }

    @Test
    fun `getStatus returns tokenExpired true when token invalid and no refresh token`() {
        val owner = user()
        val account = mlAccount(owner, refreshToken = null)
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(account))
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.validateToken(any())).thenReturn(false)

        val status = useCase.getStatus(userId)
        assertThat(status.connected).isTrue()
        assertThat(status.tokenValid).isFalse()
        assertThat(status.tokenExpired).isTrue()
        assertThat(status.error).isNotBlank()
    }

    @Test
    fun `getStatus auto-refreshes token when expired`() {
        val owner = user()
        val account = mlAccount(owner, refreshToken = "refresh-tok")
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(account))
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.validateToken(any())).thenReturn(false)
        whenever(mlApiClient.refreshToken("refresh-tok"))
            .thenReturn(MlTokenResponse(accessToken = "refreshed-token"))
        whenever(mlAccountRepository.save(any()) as MercadoLivreAccount?).thenReturn(account)

        val status = useCase.getStatus(userId)
        assertThat(status.tokenValid).isTrue()
        verify(mlApiClient).refreshToken("refresh-tok")
    }

    @Test
    fun `getStatus returns tokenExpired when refresh fails`() {
        val owner = user()
        val account = mlAccount(owner, refreshToken = "bad-refresh")
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(account))
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.validateToken(any())).thenReturn(false)
        whenever(mlApiClient.refreshToken(any())).thenThrow(RuntimeException("Refresh failed"))

        val status = useCase.getStatus(userId)
        assertThat(status.tokenValid).isFalse()
        assertThat(status.tokenExpired).isTrue()
    }

    @Test
    fun `getStatus reports affiliateConfigured when mattWord and mattTool are set`() {
        val owner = user()
        val account = mlAccount(owner, mattWord = "grupo_colossal_ofc", mattTool = "57009805")
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(account))
        whenever(valueOps.get("ml:tokenvalid:$userId")).thenReturn("valid")

        val status = useCase.getStatus(userId)
        assertThat(status.affiliateConfigured).isTrue()
    }

    // ── Disconnect ─────────────────────────────────────────────────────────────

    @Test
    fun `disconnect removes account and clears token cache`() {
        val owner = user()
        val account = mlAccount(owner)
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(account))

        useCase.disconnect(userId)
        verify(mlAccountRepository).delete(account)
        verify(redisTemplate).delete("ml:tokenvalid:$userId")
    }

    @Test
    fun `disconnect does nothing when no account`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.empty())

        useCase.disconnect(userId)
        verify(mlAccountRepository, never()).delete(any())
    }

    // ── resolveAndReplaceLinks ─────────────────────────────────────────────────

    @Test
    fun `resolveAndReplaceLinks returns unchanged text when no meli links`() {
        val result = useCase.resolveAndReplaceLinks("No links here", mlAccount(user()))
        assertThat(result).isEqualTo("No links here")
        verify(mlApiClient, never()).resolveShortLink(any())
    }

    @Test
    fun `resolveAndReplaceLinks skips substitution when affiliate params not configured`() {
        val account = mlAccount(user())  // mattWord/mattTool null

        val result = useCase.resolveAndReplaceLinks("Veja: https://meli.la/abc123", account)
        assertThat(result).isEqualTo("Veja: https://meli.la/abc123")
        verify(mlApiClient, never()).resolveShortLink(any())
    }

    @Test
    fun `resolveAndReplaceLinks keeps original link when resolved URL has no MLB product ID`() {
        val account = mlAccount(user(), mattWord = "grupo_colossal_ofc", mattTool = "57009805")
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.resolveShortLink("https://meli.la/abc123"))
            .thenReturn("https://www.mercadolivre.com.br/social/promo?matt_word=liviacorrida&matt_tool=63390150")

        val result = useCase.resolveAndReplaceLinks("Veja: https://meli.la/abc123", account)

        assertThat(result).isEqualTo("Veja: https://meli.la/abc123")
    }

    @Test
    fun `resolveAndReplaceLinks keeps original link when cached URL has no MLB product ID`() {
        val account = mlAccount(user(), mattWord = "grupo_colossal_ofc", mattTool = "57009805")
        whenever(valueOps.get(any()))
            .thenReturn("https://www.mercadolivre.com.br/produto?matt_word=other&matt_tool=99999")

        val result = useCase.resolveAndReplaceLinks("https://meli.la/xyz", account)

        assertThat(result).isEqualTo("https://meli.la/xyz")
        verify(mlApiClient, never()).resolveShortLink(any())
    }

    @Test
    fun `resolveAndReplaceLinks builds clean affiliate URL for product link with MLB ID`() {
        val account = mlAccount(user(), mattWord = "grupo_colossal_ofc", mattTool = "57009805")
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.resolveShortLink("https://meli.la/prod"))
            .thenReturn("https://produto.mercadolivre.com.br/MLB-1234567-titulo")
        whenever(mlApiClient.getItem("MLB1234567"))
            .thenReturn(MlItemDetails(
                id = "MLB1234567", title = "Produto Teste",
                permalink = "https://produto.mercadolivre.com.br/MLB-1234567-titulo",
                thumbnail = null, price = null, currencyId = null, condition = null, availableQuantity = null
            ))
        whenever(shortLinkUseCase.create(eq(userId), any()))
            .thenReturn(ShortLinkResponse(
                id = "uuid-1", code = "abc123",
                shortUrl = "https://redirectgrupo.com.br/abc123",
                targetUrl = "https://produto.mercadolivre.com.br/MLB-1234567-titulo/social/grupo_colossal_ofc?matt_word=grupo_colossal_ofc&matt_tool=57009805",
                title = null, clicks = 0, active = true, expiresAt = null, createdAt = "2024-01-01T00:00:00"
            ))

        val result = useCase.resolveAndReplaceLinks("https://meli.la/prod", account)

        assertThat(result).isEqualTo("https://redirectgrupo.com.br/abc123")
        verify(shortLinkUseCase).create(eq(userId), any())
    }

    @Test
    fun `resolveAndReplaceLinks returns original link when resolution fails`() {
        val account = mlAccount(user(), mattWord = "grupo_colossal_ofc", mattTool = "57009805")
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.resolveShortLink(any())).thenThrow(RuntimeException("Network error"))

        val result = useCase.resolveAndReplaceLinks("https://meli.la/fail", account)
        assertThat(result).isEqualTo("https://meli.la/fail")
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun user() = User(id = userId, email = "u@t.com", passwordHash = "h", name = "U")

    private fun mlAccount(
        owner: User,
        accessToken: String = "test-access-token",
        refreshToken: String? = "test-refresh-token",
        mattWord: String? = null,
        mattTool: String? = null
    ) = MercadoLivreAccount(
        id = UUID.randomUUID(),
        owner = owner,
        mlUserId = "ml-999",
        mlNickname = "TestSeller",
        accessToken = accessToken,
        refreshToken = refreshToken,
        mattWord = mattWord,
        mattTool = mattTool
    )
}
