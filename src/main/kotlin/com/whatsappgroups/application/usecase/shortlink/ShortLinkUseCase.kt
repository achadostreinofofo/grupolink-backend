package com.whatsappgroups.application.usecase.shortlink

import com.whatsappgroups.domain.model.ShortLink
import com.whatsappgroups.domain.repository.ShortLinkRepository
import com.whatsappgroups.domain.repository.UserRepository
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

data class CreateShortLinkRequest(
    @field:NotBlank val targetUrl: String,
    val code: String? = null,       // null = gerar automaticamente
    val title: String? = null,
    val expiresAt: LocalDateTime? = null
)

data class ShortLinkResponse(
    val id: String,
    val code: String,
    val shortUrl: String,
    val targetUrl: String,
    val title: String?,
    val clicks: Long,
    val active: Boolean,
    val expiresAt: String?,
    val createdAt: String
)

@Service
class ShortLinkUseCase(
    private val shortLinkRepository: ShortLinkRepository,
    private val userRepository: UserRepository,
    @Value("\${app.base-url}") private val baseUrl: String
) {

    @Transactional
    fun create(userId: UUID, request: CreateShortLinkRequest): ShortLinkResponse {
        val code = resolveCode(request.code)
        val owner = userRepository.getReferenceById(userId)

        val link = shortLinkRepository.save(
            ShortLink(
                owner     = owner,
                code      = code,
                targetUrl = request.targetUrl,
                title     = request.title,
                expiresAt = request.expiresAt
            )
        )
        return link.toResponse()
    }

    fun listByUser(userId: UUID): List<ShortLinkResponse> {
        val owner = userRepository.getReferenceById(userId)
        return shortLinkRepository.findAllByOwnerOrderByCreatedAtDesc(owner)
            .map { it.toResponse() }
    }

    @Transactional
    fun resolve(code: String): String {
        val link = shortLinkRepository.findByCode(code)
            ?: throw NoSuchElementException("Link não encontrado")

        if (!link.active) throw IllegalStateException("Link desativado")
        val expiresAt = link.expiresAt
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now())) {
            throw IllegalStateException("Link expirado")
        }

        link.clicks++
        link.updatedAt = LocalDateTime.now()
        return link.targetUrl
    }

    @Transactional
    fun delete(userId: UUID, linkId: UUID) {
        val link = shortLinkRepository.findById(linkId)
            .orElseThrow { NoSuchElementException("Link não encontrado") }
        if (link.owner.id != userId) throw IllegalAccessException("Acesso negado")
        shortLinkRepository.delete(link)
    }

    @Transactional
    fun toggleActive(userId: UUID, linkId: UUID): ShortLinkResponse {
        val link = shortLinkRepository.findById(linkId)
            .orElseThrow { NoSuchElementException("Link não encontrado") }
        if (link.owner.id != userId) throw IllegalAccessException("Acesso negado")
        link.active = !link.active
        link.updatedAt = LocalDateTime.now()
        return link.toResponse()
    }

    private fun resolveCode(requested: String?): String {
        if (!requested.isNullOrBlank()) {
            val clean = requested.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-")
            if (shortLinkRepository.existsByCode(clean)) {
                throw IllegalArgumentException("Slug '$clean' já está em uso")
            }
            return clean
        }
        // Gera código único de 7 caracteres
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        repeat(10) {
            val code = (1..7).map { chars.random() }.joinToString("")
            if (!shortLinkRepository.existsByCode(code)) return code
        }
        throw IllegalStateException("Não foi possível gerar código único")
    }

    private fun ShortLink.toResponse() = ShortLinkResponse(
        id        = id.toString(),
        code      = code,
        shortUrl  = "$baseUrl/s/$code",
        targetUrl = targetUrl,
        title     = title,
        clicks    = clicks,
        active    = active,
        expiresAt = expiresAt?.toString(),
        createdAt = createdAt.toString()
    )
}
