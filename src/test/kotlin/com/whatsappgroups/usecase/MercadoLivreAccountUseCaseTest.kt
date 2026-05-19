@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
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
    @Mock private lateinit var redisTemplate: RedisTemplate<String, String>
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var useCase: MercadoLivreAccountUseCase
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        useCase = MercadoLivreAccountUseCase(
            mlAccountRepository, userRepository, mlApiClient, redisTemplate,
            mlClientId = "test-client-id", mlRedirectUri = "http://localhost:8080/api/ml/oauth/callback")
    }

    @Test
    fun `getOAuthUrl stores state and returns ML authorization URL`() {
        val keyCaptor = argumentCaptor<String>()
        val url = useCase.getOAuthUrl(userId)

        assertThat(url).contains("auth.mercadolivre.com.br/authorization")
        assertThat(url).contains("client_id=test-client-id")
        verify(valueOps).set(keyCaptor.capture(), eq(userId.toString()), eq(10L), eq(TimeUnit.MINUTES))
        assertThat(keyCaptor.firstValue).startsWith("ml:state:")
    }

    @Test
    fun `handleCallback throws on invalid state`() {
        whenever(valueOps.get("ml:state:bad")).thenReturn(null)
        assertThatThrownBy { useCase.handleCallback("code", "bad") }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `handleCallback creates new ML account`() {
        val state = "valid-state"
        val owner = user()
        val saved = mlAccount(owner, "access-token-123")
        whenever(valueOps.get("ml:state:$state")).thenReturn(userId.toString())
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlApiClient.exchangeCodeForToken(any(), any())).thenReturn(MlTokenResponse(accessToken = "access-token-123", refreshToken = "refresh", expiresIn = 21600L))
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
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlApiClient.exchangeCodeForToken(any(), any())).thenReturn(MlTokenResponse(accessToken = "new-token"))
        whenever(mlApiClient.getMe("new-token")).thenReturn(MlUserInfo(id = "ml-999", nickname = "TestSeller"))
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(existing))
        whenever(mlAccountRepository.save(any()) as MercadoLivreAccount?).thenReturn(existing)

        val account = useCase.handleCallback("auth-code", state)
        assertThat(account.accessToken).isEqualTo("new-token")
    }

    @Test
    fun `getStatus returns connected true when account exists`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(mlAccount(owner)))

        val (connected, nickname) = useCase.getStatus(userId)
        assertThat(connected).isTrue()
        assertThat(nickname).isEqualTo("TestSeller")
    }

    @Test
    fun `getStatus returns connected false when no account`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.empty())

        assertThat(useCase.getStatus(userId).first).isFalse()
    }

    @Test
    fun `disconnect removes account`() {
        val owner = user()
        val account = mlAccount(owner)
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(account))

        useCase.disconnect(userId)
        verify(mlAccountRepository).delete(account)
    }

    @Test
    fun `disconnect does nothing when no account`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.empty())

        useCase.disconnect(userId)
        verify(mlAccountRepository, never()).delete(any())
    }

    @Test
    fun `resolveAndReplaceLinks returns unchanged text without meli links`() {
        val result = useCase.resolveAndReplaceLinks("No links here", mlAccount(user()))
        assertThat(result).isEqualTo("No links here")
        verify(mlApiClient, never()).resolveShortLink(any())
    }

    @Test
    fun `resolveAndReplaceLinks resolves and replaces on cache miss`() {
        val account = mlAccount(user())
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.resolveShortLink("https://meli.la/abc123")).thenReturn("https://produto.mercadolivre.com.br/MLB-9876543-titulo")
        whenever(mlApiClient.generateAffiliateLink("test-access-token", "MLB9876543")).thenReturn("https://affiliate.com/MLB9876543")

        val result = useCase.resolveAndReplaceLinks("Veja: https://meli.la/abc123", account)
        assertThat(result).contains("https://affiliate.com/MLB9876543")
    }

    @Test
    fun `resolveAndReplaceLinks uses cached item ID`() {
        val account = mlAccount(user())
        val keyCaptor = argumentCaptor<String>()
        whenever(valueOps.get(keyCaptor.capture())).thenReturn("MLB1111111").thenReturn(null)
        whenever(mlApiClient.generateAffiliateLink("test-access-token", "MLB1111111")).thenReturn("https://affiliate.com")

        val result = useCase.resolveAndReplaceLinks("https://meli.la/xyz", account)
        assertThat(result).contains("https://affiliate.com")
        verify(mlApiClient, never()).resolveShortLink(any())
    }

    @Test
    fun `resolveAndReplaceLinks uses fully cached affiliate link`() {
        val account = mlAccount(user())
        whenever(valueOps.get(any())).thenReturn("MLB2222222").thenReturn("https://cached-affiliate.com")

        val result = useCase.resolveAndReplaceLinks("https://meli.la/cached", account)
        assertThat(result).contains("https://cached-affiliate.com")
        verify(mlApiClient, never()).generateAffiliateLink(any(), any())
    }

    @Test
    fun `resolveAndReplaceLinks returns original link when generation fails`() {
        val account = mlAccount(user())
        whenever(valueOps.get(any())).thenReturn(null)
        whenever(mlApiClient.resolveShortLink(any())).thenReturn("https://produto.mercadolivre.com.br/MLB-1234567-titulo")
        whenever(mlApiClient.generateAffiliateLink(any(), any())).thenThrow(RuntimeException("API error"))

        assertThat(useCase.resolveAndReplaceLinks("https://meli.la/fail", account)).isEqualTo("https://meli.la/fail")
    }

    private fun user() = User(id = userId, email = "u@t.com", passwordHash = "h", name = "U")
    private fun mlAccount(owner: User, accessToken: String = "test-access-token") =
        MercadoLivreAccount(id = UUID.randomUUID(), owner = owner, mlUserId = "ml-999", mlNickname = "TestSeller", accessToken = accessToken)
}
