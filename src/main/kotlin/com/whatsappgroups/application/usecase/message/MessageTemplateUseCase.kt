package com.whatsappgroups.application.usecase.message

import com.whatsappgroups.domain.model.MessageTemplate
import com.whatsappgroups.domain.repository.MessageTemplateRepository
import com.whatsappgroups.domain.repository.UserRepository
import jakarta.validation.constraints.NotBlank
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

data class CreateTemplateRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val content: String,
    val mediaUrl: String? = null
)

data class MessageTemplateResponse(
    val id: String,
    val name: String,
    val content: String,
    val mediaUrl: String?,
    val createdAt: String
)

@Service
class MessageTemplateUseCase(
    private val templateRepository: MessageTemplateRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun create(userId: UUID, request: CreateTemplateRequest): MessageTemplateResponse {
        val owner = userRepository.getReferenceById(userId)
        val template = templateRepository.save(
            MessageTemplate(
                owner    = owner,
                name     = request.name,
                content  = request.content,
                mediaUrl = request.mediaUrl
            )
        )
        return template.toResponse()
    }

    fun listByUser(userId: UUID): List<MessageTemplateResponse> {
        val owner = userRepository.getReferenceById(userId)
        return templateRepository.findAllByOwnerOrderByCreatedAtDesc(owner)
            .map { it.toResponse() }
    }

    @Transactional
    fun update(userId: UUID, templateId: UUID, request: CreateTemplateRequest): MessageTemplateResponse {
        val template = templateRepository.findById(templateId)
            .orElseThrow { NoSuchElementException("Template não encontrado") }
        if (template.owner.id != userId) throw IllegalAccessException("Acesso negado")

        template.name      = request.name
        template.content   = request.content
        template.mediaUrl  = request.mediaUrl
        template.updatedAt = LocalDateTime.now()
        return template.toResponse()
    }

    @Transactional
    fun delete(userId: UUID, templateId: UUID) {
        val template = templateRepository.findById(templateId)
            .orElseThrow { NoSuchElementException("Template não encontrado") }
        if (template.owner.id != userId) throw IllegalAccessException("Acesso negado")
        templateRepository.delete(template)
    }

    private fun MessageTemplate.toResponse() = MessageTemplateResponse(
        id        = id.toString(),
        name      = name,
        content   = content,
        mediaUrl  = mediaUrl,
        createdAt = createdAt.toString()
    )
}
