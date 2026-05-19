package com.whatsappgroups.api

import com.whatsappgroups.infrastructure.ratelimit.RateLimitFilter
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5")
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4")
        assertThat(chain.request).isNotNull
    }

    @Test
    fun `request over limit returns 429`() {
        given(valueOps.increment(any<String>())).willReturn(6L)

        val request  = MockHttpServletRequest("GET", "/r/test-slug")
        val response = MockHttpServletResponse()
        val chain    = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(429)
        assertThat(response.contentAsString).contains("Muitas requisições")
        assertThat(chain.request).isNull()
    }

    @Test
    fun `non-redirect path is not filtered — Redis not called`() {
        val request  = MockHttpServletRequest("GET", "/api/auth/login")
        val response = MockHttpServletResponse()
        val chain    = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(valueOps, never()).increment(any())
        assertThat(chain.request).isNotNull
    }

    @Test
    fun `redirect path is rate-limited`() {
        given(valueOps.increment(any<String>())).willReturn(1L)
        given(redisTemplate.expire(any<String>(), any<Duration>())).willReturn(true)

        val request  = MockHttpServletRequest("GET", "/r/my-slug")
        val response = MockHttpServletResponse()
        val chain    = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(valueOps).increment(any())
    }

    @Test
    fun `short link path is rate-limited`() {
        given(valueOps.increment(any<String>())).willReturn(1L)
        given(redisTemplate.expire(any<String>(), any<Duration>())).willReturn(true)

        val request  = MockHttpServletRequest("GET", "/s/abc123")
        val response = MockHttpServletResponse()
        val chain    = MockFilterChain()

        filter.doFilter(request, response, chain)

        verify(valueOps).increment(any())
    }

    @Test
    fun `first request in window sets redis expiry`() {
        given(valueOps.increment(any<String>())).willReturn(1L)
        given(redisTemplate.expire(any<String>(), any<Duration>())).willReturn(true)

        val request  = MockHttpServletRequest("GET", "/r/slug")
        request.remoteAddr = "10.0.0.1"

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        verify(redisTemplate).expire(any(), any<Duration>())
    }

    @Test
    fun `subsequent requests in same window do not reset expiry`() {
        given(valueOps.increment(any<String>())).willReturn(2L)

        val request = MockHttpServletRequest("GET", "/r/slug")
        request.remoteAddr = "10.0.0.1"

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        verify(redisTemplate, never()).expire(any(), any<Duration>())
    }

    @Test
    fun `X-Forwarded-For header is used for IP`() {
        given(valueOps.increment(any<String>())).willReturn(1L)
        given(redisTemplate.expire(any<String>(), any<Duration>())).willReturn(true)

        val request = MockHttpServletRequest("GET", "/r/slug")
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
        request.remoteAddr = "10.0.0.1"

        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

        verify(valueOps).increment(argThat { contains("203.0.113.5") })
    }

    @Test
    fun `rate limit headers show zero remaining when at limit`() {
        given(valueOps.increment(any<String>())).willReturn(5L)

        val request  = MockHttpServletRequest("GET", "/r/slug")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0")
    }
}
