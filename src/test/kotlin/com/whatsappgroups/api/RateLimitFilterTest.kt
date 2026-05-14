package com.whatsappgroups.api

import com.whatsappgroups.infrastructure.ratelimit.RateLimitFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.verify
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

@ExtendWith(MockitoExtension::class)
class RateLimitFilterTest {

    @Mock private lateinit var redisTemplate: RedisTemplate<String, String>
    @Mock private lateinit var valueOps: ValueOperations<String, String>

    private lateinit var filter: RateLimitFilter

    @BeforeEach
    fun setUp() {
        given(redisTemplate.opsForValue()).willReturn(valueOps)
        filter = RateLimitFilter(redisTemplate, limit = 5L, windowSeconds = 60L)
    }

    @Test
    fun `request under limit passes through`() {
        given(valueOps.increment(any<String>())).willReturn(1L)
        given(redisTemplate.expire(any<String>(), any<Duration>())).willReturn(true)

        val request  = MockHttpServletRequest("GET", "/r/test-slug")
        val response = MockHttpServletResponse()
        val chain    = MockFilterChain()

        filter.doFilterInternal(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5")
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4")
        assertThat(chain.request).isNotNull   // chain foi executada
    }

    @Test
    fun `request over limit returns 429`() {
        given(valueOps.increment(any<String>())).willReturn(6L)  // > limit de 5

        val request  = MockHttpServletRequest("GET", "/r/test-slug")
        val response = MockHttpServletResponse()
        val chain    = MockFilterChain()

        filter.doFilterInternal(request, response, chain)

        assertThat(response.status).isEqualTo(429)
        assertThat(response.contentAsString).contains("Muitas requisições")
        assertThat(chain.request).isNull   // chain NÃO foi executada
    }

    @Test
    fun `non-redirect path is not filtered`() {
        val request = MockHttpServletRequest("GET", "/api/auth/login")
        assertThat(filter.shouldNotFilter(request)).isTrue()
    }

    @Test
    fun `redirect path is filtered`() {
        val request = MockHttpServletRequest("GET", "/r/my-slug")
        assertThat(filter.shouldNotFilter(request)).isFalse()
    }

    @Test
    fun `short link path is filtered`() {
        val request = MockHttpServletRequest("GET", "/s/abc123")
        assertThat(filter.shouldNotFilter(request)).isFalse()
    }

    @Test
    fun `first request in window sets redis expiry`() {
        given(valueOps.increment(any<String>())).willReturn(1L)
        given(redisTemplate.expire(any<String>(), any<Duration>())).willReturn(true)

        val request  = MockHttpServletRequest("GET", "/r/slug")
        request.remoteAddr = "10.0.0.1"
        val response = MockHttpServletResponse()
        val chain    = MockFilterChain()

        filter.doFilterInternal(request, response, chain)

        verify(redisTemplate).expire(any(), any<Duration>())
    }

    @Test
    fun `X-Forwarded-For header is used for IP`() {
        given(valueOps.increment(any<String>())).willReturn(1L)
        given(redisTemplate.expire(any<String>(), any<Duration>())).willReturn(true)

        val request = MockHttpServletRequest("GET", "/r/slug")
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
        request.remoteAddr = "10.0.0.1"

        filter.doFilterInternal(request, MockHttpServletResponse(), MockFilterChain())

        // Verifica que o key usa o IP real (203.0.113.5), não o proxy
        verify(valueOps).increment(org.mockito.kotlin.argThat { contains("203.0.113.5") })
    }
}
