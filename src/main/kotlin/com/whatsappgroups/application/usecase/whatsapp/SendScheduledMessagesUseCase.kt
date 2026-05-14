package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.domain.model.MessageStatus
import com.whatsappgroups.domain.model.ScheduledMessage
import com.whatsappgroups.domain.repository.RedirectLogRepository
import com.whatsappgroups.domain.repository.ScheduledMessageRepository
import com.whatsappgroups.domain.repository.WhatsappAccountRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappCloudApiClient
import com.whatsappgroups.infrastructure.whatsapp.WhatsappMediaMessage
import com.whatsappgroups.infrastructure.whatsapp.WhatsappTextMessage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SendScheduledMessagesUseCase(
    private val scheduledMessageRepository: ScheduledMessageRepository,
    private val whatsappAccountRepository: WhatsappAccountRepository,
    private val redirectLogRepository: RedirectLogRepository,
    private val apiClient: WhatsappCloudApiClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Executa a cada minuto — verifica mensagens prontas para envio
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
        val accounts = whatsappAccountRepository.findAllByOwnerAndActive(message.owner, true)
        if (accounts.isEmpty()) {
            failMessage(message, "Nenhuma conta WhatsApp conectada")
            return
        }

        // Coleta números únicos que interagiram com a estrutura (via RedirectLog)
        val recipients = message.structure?.let { structure ->
            redirectLogRepository
                .countByGroupSince(structure, LocalDateTime.now().minusYears(1))
                .map { it[0].toString() }
                .distinct()
        } ?: emptyList()

        if (recipients.isEmpty()) {
            failMessage(message, "Nenhum número de destinatário encontrado para esta estrutura")
            return
        }

        val account = accounts.first()
        var successCount = 0

        recipients.forEach { phoneNumber ->
            val ok = if (message.mediaUrl != null) {
                apiClient.sendImageMessage(
                    account.phoneNumberId, account.accessToken,
                    WhatsappMediaMessage(to = phoneNumber, mediaUrl = message.mediaUrl!!, caption = message.content)
                )
            } else {
                apiClient.sendTextMessage(
                    account.phoneNumberId, account.accessToken,
                    WhatsappTextMessage(to = phoneNumber, text = message.content)
                )
            }
            if (ok) successCount++
        }

        message.status = MessageStatus.SENT
        message.executedAt = LocalDateTime.now()
        message.updatedAt = LocalDateTime.now()
        log.info("Mensagem ${message.id} enviada para $successCount/${recipients.size} destinatários")
    }

    private fun failMessage(message: ScheduledMessage, reason: String) {
        message.status = MessageStatus.FAILED
        message.errorMessage = reason
        message.executedAt = LocalDateTime.now()
        message.updatedAt = LocalDateTime.now()
        log.warn("Mensagem ${message.id} falhou: $reason")
    }
}
