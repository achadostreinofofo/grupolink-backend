package com.whatsappgroups.application.usecase.message

import com.whatsappgroups.application.dto.CreateScheduledMessageRequest
import com.whatsappgroups.application.dto.ScheduledMessageResponse
import com.whatsappgroups.application.dto.UpdateScheduledMessageRequest
import com.whatsappgroups.application.usecase.whatsapp.BroadcastUseCase
import com.whatsappgroups.application.dto.BroadcastMessageRequest
import com.whatsappgroups.domain.model.MessageStatus
import com.whatsappgroups.domain.model.ScheduledMessage
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
    private val structureRepository: StructureRepository,
    private val broadcastUseCase: BroadcastUseCase
) {

    @Transactional
    fun create(userId: UUID, request: CreateScheduledMessageRequest): ScheduledMessageResponse {
        val owner = userRepository.getReferenceById(userId)

        val structure = request.structureId?.let {
            structureRepository.findById(UUID.fromString(it))
                .orElseThrow { NoSuchElementException("Estrutura não encontrada: $it") }
                .also { s -> if (s.owner.id != userId) throw IllegalAccessException("Acesso negado") }
        }

        val status = if (request.scheduledAt != null) MessageStatus.PENDING else MessageStatus.DRAFT

        val message = messageRepository.save(
            ScheduledMessage(
                owner       = owner,
                structure   = structure,
                title       = request.title,
                content     = request.content,
                mediaUrl    = request.mediaUrl,
                status      = status,
                scheduledAt = request.scheduledAt
            )
        )

        return message.toResponse()
    }

    @Transactional
    fun update(userId: UUID, messageId: UUID, request: UpdateScheduledMessageRequest): ScheduledMessageResponse {
        val message = findOwned(userId, messageId)

        if (message.status == MessageStatus.SENT || message.status == MessageStatus.FAILED) {
            throw IllegalStateException("Não é possível editar uma mensagem já processada")
        }

        message.title       = request.title
        message.content     = request.content
        message.mediaUrl    = request.mediaUrl
        message.scheduledAt = request.scheduledAt
        message.status      = if (request.scheduledAt != null) MessageStatus.PENDING else MessageStatus.DRAFT
        message.updatedAt   = LocalDateTime.now()

        return message.toResponse()
    }

    @Transactional
    fun sendNow(userId: UUID, messageId: UUID): ScheduledMessageResponse {
        val message = findOwned(userId, messageId)

        val structure = message.structure
            ?: throw IllegalStateException("Mensagem não está associada a uma estrutura")

        if (message.status == MessageStatus.SENT) throw IllegalStateException("Mensagem já foi enviada")
        if (message.status == MessageStatus.CANCELLED) throw IllegalStateException("Mensagem foi cancelada")

        // Triggers async broadcast via RabbitMQ
        val messageType = if (message.mediaUrl != null) "IMAGE" else "TEXT"
        broadcastUseCase.broadcast(
            userId      = userId,
            structureId = structure.id!!,
            request     = BroadcastMessageRequest(
                messageType = messageType,
                content     = message.content,
                mediaUrl    = message.mediaUrl
            )
        )

        message.status      = MessageStatus.SENT
        message.executedAt  = LocalDateTime.now()
        message.updatedAt   = LocalDateTime.now()

        return message.toResponse()
    }

    fun listByUser(userId: UUID): List<ScheduledMessageResponse> {
        val owner = userRepository.getReferenceById(userId)
        return messageRepository.findAllByOwner(owner)
            .sortedByDescending { it.updatedAt }
            .map { it.toResponse() }
    }

    fun listByStructure(userId: UUID, structureId: UUID): List<ScheduledMessageResponse> {
        val owner = userRepository.getReferenceById(userId)
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        return messageRepository.findAllByOwnerAndStructureId(owner, structureId)
            .sortedByDescending { it.updatedAt }
            .map { it.toResponse() }
    }

    @Transactional
    fun cancel(userId: UUID, messageId: UUID) {
        val message = findOwned(userId, messageId)
        if (message.status != MessageStatus.PENDING && message.status != MessageStatus.DRAFT) {
            throw IllegalStateException("Só é possível cancelar mensagens com status DRAFT ou PENDING")
        }
        message.status    = MessageStatus.CANCELLED
        message.updatedAt = LocalDateTime.now()
    }

    @Transactional
    fun delete(userId: UUID, messageId: UUID) {
        val message = findOwned(userId, messageId)
        messageRepository.delete(message)
    }

    private fun findOwned(userId: UUID, messageId: UUID): ScheduledMessage {
        val message = messageRepository.findById(messageId)
            .orElseThrow { NoSuchElementException("Mensagem não encontrada") }
        if (message.owner.id != userId) throw IllegalAccessException("Acesso negado")
        return message
    }

    private fun ScheduledMessage.toResponse() = ScheduledMessageResponse(
        id            = id.toString(),
        title         = title,
        content       = content,
        mediaUrl      = mediaUrl,
        status        = status.name,
        scheduledAt   = scheduledAt?.toString(),
        executedAt    = executedAt?.toString(),
        errorMessage  = errorMessage,
        structureId   = structure?.id?.toString(),
        structureName = structure?.name
    )
}
