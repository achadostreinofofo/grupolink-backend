package com.whatsappgroups.infrastructure.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

// Responsável por mapear cookie UUID -> grupo ID em memória, evitando consulta ao banco a cada clique
@Service
class RedisSessionService(private val redisTemplate: RedisTemplate<String, String>) {

    companion object {
        private const val PREFIX_COOKIE_GROUP = "cookie:group:"
        private const val PREFIX_STRUCTURE_COUNTER = "structure:counter:"
        private val TTL = Duration.ofDays(90)
    }

    fun getGroupForCookie(cookieId: String, structureId: UUID): UUID? =
        redisTemplate.opsForValue()
            .get("$PREFIX_COOKIE_GROUP${structureId}:${cookieId}")
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    fun bindCookieToGroup(cookieId: String, structureId: UUID, groupId: UUID) {
        redisTemplate.opsForValue().set(
            "$PREFIX_COOKIE_GROUP${structureId}:${cookieId}",
            groupId.toString(),
            TTL
        )
    }

    // Contador atômico de round-robin por estrutura — garante distribuição entre grupos sem race condition
    fun getAndIncrementRoundRobinIndex(structureId: UUID): Long =
        redisTemplate.opsForValue().increment("$PREFIX_STRUCTURE_COUNTER${structureId}") ?: 0L

    fun deleteCookieBinding(cookieId: String, structureId: UUID) {
        redisTemplate.delete("$PREFIX_COOKIE_GROUP${structureId}:${cookieId}")
    }
}
