package com.whatsappgroups.application.usecase.message

import com.whatsappgroups.application.dto.CreateScheduledMessageRequest
import com.whatsappgroups.application.dto.ScheduledMessageResponse
import com.whatsappgroups.domain.model.MessageStatus
import com.whatsappgroups.domain.model.ScheduledMessage
import com.whatsappgroups.domain.model.limits
import com.whatsappgroups.domain.repository.ScheduledMessageRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class ScheduledMessageUseCase(
    private val messageRepository: ScheduledMessageRepository,
    private val userRepository: UserRepository,
    private val structureRepository: StructureRepository
) {

    @Transactional
    fun create(userId: UUID, request: CreateScheduledMessageRequest): ScheduledMessageResponse {
        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val limits = owner.plan.limits()
        if (limits.maxSavedMessages != Int.MAX_VALUE) {
            val savedCount = messageRepository.countByOwnerAndStatus(owner, MessageStatus.PENDING)
            if (savedCount >= limits.maxSavedMessages) {
                throw IllegalArgumentException(
                    "Seu plano ${owner.plan.name} permite no máximo ${limits.maxSavedMessages} mensagens salvas. " +
                    "Cancele mensagens antigas ou faça upgrade."
                )
            }
        }

        val structure = request.structureId?.let {
            structureRepository.findById(UUID.fromString(it))
                .orElseThrow { NoSuchElementException("Estrutura não encontrada: $it") }
                .also { s -> if (s.owner.id != userId) throw IllegalAccessException("Acesso negado") }
        }

        val message = messageRepository.save(
            ScheduledMessage(
                owner       = owner,
                structure   = structure,
                title       = request.title,
                content     = request.content,
                mediaUrl    = request.mediaUrl,
                scheduledAt = request.scheduledAt
            )
        )

        return message.toResponse()
    }

    fun listByUser(userId: UUID): List<ScheduledMessageResponse> {
        val owner = userRepository.getReferenceById(userId)
        return messageRepository.findAllByOwner(owner)
            .sortedByDescending { it.scheduledAt }
            .map { it.toResponse() }
    }

    @Transactional
    fun cancel(userId: UUID, messageId: UUID) {
        val message = messageRepository.findById(messageId)
            .orElseThrow { NoSuchElementException("Mensagem não encontrada") }

        if (message.owner.id != userId) throw IllegalAccessException("Acesso negado")
        if (message.status != MessageStatus.PENDING) {
            throw IllegalStateException("Só é possível cancelar mensagens com status PENDING")
        }

        message.status    = MessageStatus.CANCELLED
        message.updatedAt = LocalDateTime.now()
    }

    private fun ScheduledMessage.toResponse() = ScheduledMessageResponse(
        id            = id.toString(),
        title         = title,
        content       = content,
        mediaUrl      = mediaUrl,
        status        = status.name,
        scheduledAt   = scheduledAt.toString(),
        executedAt    = executedAt?.toString(),
        errorMessage  = errorMessage,
        structureId   = structure?.id?.toString(),
        structureName = structure?.name
    )
}
