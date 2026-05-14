package com.whatsappgroups.infrastructure.ratelimit

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

@Component
@Order(1)
class RateLimitFilter(
    private val redisTemplate: RedisTemplate<String, String>,
    @Value("\${app.rate-limit.redirect-limit:30}") private val limit: Long,
    @Value("\${app.rate-limit.window-seconds:60}")  private val windowSeconds: Long
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !path.startsWith("/r/") && !path.startsWith("/s/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val ip  = resolveClientIp(request)
        val key = buildKey(ip)

        val count = redisTemplate.opsForValue().increment(key) ?: 1L
        if (count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
        }

        val remaining = (limit - count).coerceAtLeast(0L)
        response.setHeader("X-RateLimit-Limit",     limit.toString())
        response.setHeader("X-RateLimit-Remaining", remaining.toString())
        response.setHeader("X-RateLimit-Window",    "${windowSeconds}s")

        if (count > limit) {
            log.warn("Rate limit atingido: ip=$ip, count=$count, path=${request.requestURI}")
            response.status      = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":"Muitas requisições. Aguarde e tente novamente.","retryAfterSeconds":$windowSeconds}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun buildKey(ip: String): String {
        val bucket = System.currentTimeMillis() / (windowSeconds * 1000)
        return "rl:$ip:$bucket"
    }

    private fun resolveClientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
}