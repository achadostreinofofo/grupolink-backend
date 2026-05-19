@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.usecase.shortlink.CreateShortLinkRequest
import com.whatsappgroups.application.usecase.shortlink.ShortLinkUseCase
import com.whatsappgroups.domain.model.ShortLink
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.repository.ShortLinkRepository
import com.whatsappgroups.domain.repository.UserRepository
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
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShortLinkUseCaseTest {

    @Mock private lateinit var shortLinkRepository: ShortLinkRepository
    @Mock private lateinit var userRepository: UserRepository

    private lateinit var useCase: ShortLinkUseCase
    private val userId = UUID.randomUUID()
    private val linkId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        useCase = ShortLinkUseCase(shortLinkRepository, userRepository, "http://localhost:8080")
    }

    @Test
    fun `create with custom code saves and returns response`() {
        val owner = user()
        val saved = ShortLink(id = linkId, owner = owner, code = "my-link", targetUrl = "https://example.com")
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(shortLinkRepository.existsByCode("my-link")).thenReturn(false)
        whenever(shortLinkRepository.save(any()) as ShortLink?).thenReturn(saved)

        val result = useCase.create(userId, CreateShortLinkRequest(targetUrl = "https://example.com", code = "my-link"))

        assertThat(result.code).isEqualTo("my-link")
        assertThat(result.shortUrl).isEqualTo("http://localhost:8080/s/my-link")
    }

    @Test
    fun `create sanitizes special chars in code`() {
        val owner = user()
        val saved = ShortLink(id = linkId, owner = owner, code = "promo-offer", targetUrl = "https://example.com")
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(shortLinkRepository.existsByCode("promo-offer")).thenReturn(false)
        whenever(shortLinkRepository.save(any()) as ShortLink?).thenReturn(saved)

        // "PROMO OFFER" → lowercase → replace space with '-' → "promo-offer"
        useCase.create(userId, CreateShortLinkRequest(targetUrl = "https://example.com", code = "PROMO OFFER"))

        verify(shortLinkRepository).save(check { assertThat(it.code).isEqualTo("promo-offer") })
    }

    @Test
    fun `create throws on duplicate code`() {
        whenever(shortLinkRepository.existsByCode("taken")).thenReturn(true)

        assertThatThrownBy { useCase.create(userId, CreateShortLinkRequest(targetUrl = "https://x.com", code = "taken")) }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("em uso")

        verify(shortLinkRepository, never()).save(any())
    }

    @Test
    fun `create with null code generates a 7-char code`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(shortLinkRepository.existsByCode(any())).thenReturn(false)
        whenever(shortLinkRepository.save(any()) as ShortLink?).thenAnswer { inv ->
            val link = inv.getArgument<ShortLink>(0)
            ShortLink(id = linkId, owner = owner, code = link.code, targetUrl = link.targetUrl)
        }

        val result = useCase.create(userId, CreateShortLinkRequest(targetUrl = "https://example.com"))

        assertThat(result.code).hasSize(7)
    }

    @Test
    fun `resolve active link increments clicks`() {
        val link = shortLink(active = true)
        whenever(shortLinkRepository.findByCode("abc1234")).thenReturn(link)
        assertThat(useCase.resolve("abc1234")).isEqualTo("https://target.com")
        assertThat(link.clicks).isEqualTo(1)
    }

    @Test
    fun `resolve throws when link not found`() {
        whenever(shortLinkRepository.findByCode("missing")).thenReturn(null)
        assertThatThrownBy { useCase.resolve("missing") }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `resolve throws when link inactive`() {
        whenever(shortLinkRepository.findByCode("off")).thenReturn(shortLink(active = false))
        assertThatThrownBy { useCase.resolve("off") }
            .isInstanceOf(IllegalStateException::class.java).hasMessageContaining("desativado")
    }

    @Test
    fun `resolve throws when link expired`() {
        whenever(shortLinkRepository.findByCode("old")).thenReturn(shortLink(active = true, expiresAt = LocalDateTime.now().minusDays(1)))
        assertThatThrownBy { useCase.resolve("old") }
            .isInstanceOf(IllegalStateException::class.java).hasMessageContaining("expirado")
    }

    @Test
    fun `resolve succeeds with future expiry`() {
        whenever(shortLinkRepository.findByCode("future")).thenReturn(shortLink(active = true, expiresAt = LocalDateTime.now().plusDays(7)))
        assertThat(useCase.resolve("future")).isEqualTo("https://target.com")
    }

    @Test
    fun `delete removes owned link`() {
        val link = shortLink()
        whenever(shortLinkRepository.findById(linkId)).thenReturn(Optional.of(link))
        useCase.delete(userId, linkId)
        verify(shortLinkRepository).delete(link)
    }

    @Test
    fun `delete throws for different user`() {
        val other = User(id = UUID.randomUUID(), email = "o@x.com", passwordHash = "h", name = "O")
        whenever(shortLinkRepository.findById(linkId)).thenReturn(Optional.of(ShortLink(id = linkId, owner = other, code = "x", targetUrl = "https://x.com")))
        assertThatThrownBy { useCase.delete(userId, linkId) }.isInstanceOf(IllegalAccessException::class.java)
    }

    @Test
    fun `delete throws when not found`() {
        whenever(shortLinkRepository.findById(linkId)).thenReturn(Optional.empty())
        assertThatThrownBy { useCase.delete(userId, linkId) }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `toggleActive flips true to false`() {
        val link = shortLink(active = true)
        whenever(shortLinkRepository.findById(linkId)).thenReturn(Optional.of(link))
        assertThat(useCase.toggleActive(userId, linkId).active).isFalse()
    }

    @Test
    fun `toggleActive flips false to true`() {
        val link = shortLink(active = false)
        whenever(shortLinkRepository.findById(linkId)).thenReturn(Optional.of(link))
        assertThat(useCase.toggleActive(userId, linkId).active).isTrue()
    }

    @Test
    fun `listByUser returns all links`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(shortLinkRepository.findAllByOwnerOrderByCreatedAtDesc(owner)).thenReturn(listOf(shortLink(), shortLink()))
        assertThat(useCase.listByUser(userId)).hasSize(2)
    }

    private fun user() = User(id = userId, email = "u@t.com", passwordHash = "h", name = "U")
    private fun shortLink(active: Boolean = true, expiresAt: LocalDateTime? = null) =
        ShortLink(id = linkId, owner = user(), code = "abc1234", targetUrl = "https://target.com", active = active, expiresAt = expiresAt)
}
