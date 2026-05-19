package com.whatsappgroups.security

import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtTokenProviderTest {

    private lateinit var provider: JwtTokenProvider

    @BeforeEach
    fun setUp() {
        provider = JwtTokenProvider(
            secret = "test-secret-at-least-256-bits-long-for-hmac-sha256-algorithm-padding",
            expirationMs = 3600000L
        )
    }

    @Test
    fun `generateToken returns non-blank JWT`() {
        val token = provider.generateToken(UUID.randomUUID())
        assertThat(token).isNotBlank()
        assertThat(token.split(".")).hasSize(3)
    }

    @Test
    fun `extractUserId returns same UUID used to generate`() {
        val userId = UUID.randomUUID()
        val token = provider.generateToken(userId)
        assertThat(provider.extractUserId(token)).isEqualTo(userId)
    }

    @Test
    fun `isValid returns true for fresh token`() {
        val token = provider.generateToken(UUID.randomUUID())
        assertThat(provider.isValid(token)).isTrue()
    }

    @Test
    fun `isValid returns false for expired token`() {
        val expired = JwtTokenProvider(
            secret = "test-secret-at-least-256-bits-long-for-hmac-sha256-algorithm-padding",
            expirationMs = -1L
        )
        val token = expired.generateToken(UUID.randomUUID())
        assertThat(expired.isValid(token)).isFalse()
    }

    @Test
    fun `isValid returns false for garbage string`() {
        assertThat(provider.isValid("not.a.token")).isFalse()
    }

    @Test
    fun `extractUserId returns null for invalid token`() {
        assertThat(provider.extractUserId("garbage")).isNull()
    }
}
