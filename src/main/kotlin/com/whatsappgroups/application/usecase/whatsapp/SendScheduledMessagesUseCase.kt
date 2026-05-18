package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.application.dto.BroadcastMessageRequest
import com.whatsappgroups.domain.model.MessageStatus
import com.whatsappgroups.domain.model.ScheduledMessage
import com.whatsappgroups.domain.repository.ScheduledMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SendScheduledMessagesUseCase(
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val broadcastUseCase: BroadcastUseCase
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    fun processScheduledMessages() {
        val pending = scheduledMessageRepository
            .findAllByStatusAndScheduledAtBefore(MessageStatus.PENDING, LocalDateTime.now())

        if (pending.isEmpty()) return

        log.info("Processando ${pending.size} mensagem(ns) agendada(s)")
        pending.forEach { sendMessage(it) }
    }

    private fun sendMessage(message: ScheduledMessage) {
        val structure = message.structure
        if (structure == null) {
            failMessage(message, "Mensagem não está associada a uma estrutura")
            return
        }

        try {
            val messageType = if (message.mediaUrl != null) "IMAGE" else "TEXT"
            broadcastUseCase.broadcast(
                userId      = message.owner.id!!,
                structureId = structure.id!!,
                request     = BroadcastMessageRequest(
                    messageType = messageType,
                    content     = message.content,
                    mediaUrl    = message.mediaUrl
                )
            )
            message.status    = MessageStatus.SENT
            message.executedAt = LocalDateTime.now()
            message.updatedAt  = LocalDateTime.now()
            log.info("Mensagem ${message.id} ('${message.title}') enviada via broadcast para estrutura ${structure.name}")
        } catch (e: Exception) {
            failMessage(message, e.message ?: "Erro desconhecido")
        }
    }

    private fun failMessage(message: ScheduledMessage, reason: String) {
        message.status       = MessageStatus.FAILED
        message.errorMessage = reason
        message.executedAt   = LocalDateTime.now()
        message.updatedAt    = LocalDateTime.now()
        log.warn("Mensagem ${message.id} ('${message.title}') falhou: $reason")
    }
}
