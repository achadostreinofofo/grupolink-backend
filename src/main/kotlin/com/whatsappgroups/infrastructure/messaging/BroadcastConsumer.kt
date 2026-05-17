package com.whatsappgroups.infrastructure.messaging

import com.whatsappgroups.domain.model.BroadcastGroupSendStatus
import com.whatsappgroups.domain.model.BroadcastMessageType
import com.whatsappgroups.domain.model.BroadcastStatus
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.repository.BroadcastGroupResultRepository
import com.whatsappgroups.domain.repository.MessageBroadcastRepository
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

data class BroadcastMessage(
    val broadcastId: String,
    val userId: String
)

@Component
class BroadcastConsumer(
    private val broadcastRepository: MessageBroadcastRepository,
    private val resultRepository: BroadcastGroupResultRepository,
    private val sessionRepository: WhatsappWebSessionRepository,
    private val webServiceClient: WhatsappWebServiceClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitMQConfig.BROADCAST_QUEUE])
    @Transactional
    fun consume(message: BroadcastMessage) {
        val broadcastId = UUID.fromString(message.broadcastId)
        val userId = UUID.fromString(message.userId)

        val broadcast = broadcastRepository.findById(broadcastId).orElse(null) ?: run {
            log.warn("Broadcast $broadcastId not found, skipping")
            return
        }

        broadcast.status = BroadcastStatus.IN_PROGRESS
        broadcast.executedAt = LocalDateTime.now()
        broadcastRepository.save(broadcast)

        // Find authenticated WhatsApp Web session for this user
        val ownerRef = sessionRepository.findAll()
            .firstOrNull { it.owner.id == userId && it.status == WebSessionStatus.AUTHENTICATED }

        if (ownerRef == null) {
            log.warn("No authenticated WhatsApp Web session for user $userId, broadcast $broadcastId failed")
            broadcast.status = BroadcastStatus.FAILED
            broadcast.errorMessage = "Nenhuma sessão WhatsApp Web autenticada encontrada"
            broadcastRepository.save(broadcast)
            return
        }

        val sessionId = ownerRef.sessionId
        val results = resultRepository.findAllByBroadcastId(broadcastId)

        var successCount = 0
        var failCount = 0

        for (result in results) {
            val group = result.group
            val whatsappGroupId = group.whatsappGroupId

            if (whatsappGroupId == null) {
                result.status = BroadcastGroupSendStatus.FAILED
                result.errorMessage = "Grupo sem ID WhatsApp associado"
                resultRepository.save(result)
                failCount++
                continue
            }

            val sent = try {
                when (broadcast.messageType) {
                    BroadcastMessageType.TEXT  ->
                        webServiceClient.sendTextMessage(sessionId, whatsappGroupId, broadcast.content)
                    BroadcastMessageType.IMAGE ->
                        webServiceClient.sendImageMessage(sessionId, whatsappGroupId, broadcast.mediaUrl ?: "", broadcast.content)
                }
            } catch (e: Exception) {
                log.error("Error sending message to group ${group.id} for broadcast $broadcastId: ${e.message}")
                false
            }

            if (sent) {
                result.status = BroadcastGroupSendStatus.SUCCESS
                successCount++
            } else {
                result.status = BroadcastGroupSendStatus.FAILED
                result.errorMessage = "Falha ao enviar mensagem"
                failCount++
            }

            resultRepository.save(result)
            broadcast.groupsProcessed++
            broadcast.groupsSuccessful = successCount
            broadcast.groupsFailed = failCount
            broadcastRepository.save(broadcast)

            // Small delay to avoid rate limiting
            Thread.sleep(500)
        }

        broadcast.status = BroadcastStatus.COMPLETED
        broadcastRepository.save(broadcast)

        log.info("Broadcast $broadcastId completed: $successCount success, $failCount failed")
    }
}
