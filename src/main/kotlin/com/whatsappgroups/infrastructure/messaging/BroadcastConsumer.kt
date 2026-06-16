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

        // Todas as contas (sessões) autenticadas do usuário — usadas em rodízio (anti-ban)
        val userSessions = sessionRepository.findAll()
            .filter { it.owner.id == userId && it.status == WebSessionStatus.AUTHENTICATED }
            .sortedBy { it.createdAt }

        if (userSessions.isEmpty()) {
            log.warn("No authenticated WhatsApp Web session for user $userId, broadcast $broadcastId failed")
            broadcast.status = BroadcastStatus.FAILED
            broadcast.errorMessage = "Nenhuma sessão WhatsApp Web autenticada encontrada"
            broadcastRepository.save(broadcast)
            return
        }

        // Mantém apenas as que estão de fato autenticadas no whatsapp-service
        val sessions = userSessions.filter { webServiceClient.getSessionStatus(it.sessionId).status == "authenticated" }
        if (sessions.isEmpty()) {
            log.warn("Broadcast $broadcastId — nenhuma sessão pronta no serviço, recriando a principal e adiando")
            webServiceClient.createSession(userSessions.first().sessionId)
            broadcast.status       = BroadcastStatus.FAILED
            broadcast.errorMessage = "Sessão WhatsApp reconectando. Aguarde alguns segundos e tente novamente."
            broadcastRepository.save(broadcast)
            return
        }

        // Conta principal (fallback do rodízio, ex.: grupos legados onde só ela é membro)
        val primarySessionId = sessions.first().sessionId
        log.info("Broadcast $broadcastId — ${sessions.size} conta(s) para rodízio (user $userId)")

        val results = resultRepository.findAllByBroadcastId(broadcastId)
        log.info("Broadcast $broadcastId has ${results.size} group(s) to process")

        var successCount = 0
        var failCount = 0
        var rrIndex = 0

        fun sendVia(sid: String, gid: String): Boolean = try {
            when (broadcast.messageType) {
                BroadcastMessageType.TEXT  -> webServiceClient.sendTextMessage(sid, gid, broadcast.content)
                BroadcastMessageType.IMAGE -> webServiceClient.sendImageMessage(sid, gid, broadcast.mediaUrl ?: "", broadcast.content)
            }
        } catch (e: Exception) {
            log.error("Broadcast $broadcastId — erro enviando para $gid via $sid: ${e.message}")
            false
        }

        for (result in results) {
            val group = result.group
            val whatsappGroupId = group.whatsappGroupId

            if (whatsappGroupId == null) {
                val msg = "Grupo '${group.name}' (${group.id}) não tem ID WhatsApp — " +
                    "recrie o grupo pela plataforma para gerar o ID real no WhatsApp."
                log.error("BROADCAST $broadcastId SKIPPED GROUP: $msg")
                result.status = BroadcastGroupSendStatus.FAILED
                result.errorMessage = msg
                resultRepository.save(result)
                failCount++
                continue
            }

            // Rodízio: cada grupo usa a próxima conta; se falhar, cai para a conta principal
            val chosen = sessions[rrIndex % sessions.size].sessionId
            rrIndex++
            log.info("Broadcast $broadcastId → grupo '${group.name}' ($whatsappGroupId) via $chosen")

            var sent = sendVia(chosen, whatsappGroupId)
            if (!sent && chosen != primarySessionId) {
                log.warn("Broadcast $broadcastId — rodízio falhou em $chosen, tentando conta principal $primarySessionId")
                sent = sendVia(primarySessionId, whatsappGroupId)
            }

            if (sent) {
                log.info("Broadcast $broadcastId → group '${group.name}' SUCCESS")
                result.status = BroadcastGroupSendStatus.SUCCESS
                successCount++
            } else {
                log.warn("Broadcast $broadcastId → group '${group.name}' FAILED")
                result.status = BroadcastGroupSendStatus.FAILED
                result.errorMessage = "Falha ao enviar via whatsapp-service"
                failCount++
            }

            resultRepository.save(result)
            broadcast.groupsProcessed++
            broadcast.groupsSuccessful = successCount
            broadcast.groupsFailed = failCount
            broadcastRepository.save(broadcast)

            Thread.sleep(500)
        }

        broadcast.status = BroadcastStatus.COMPLETED
        broadcastRepository.save(broadcast)

        log.info("Broadcast $broadcastId COMPLETED: $successCount success, $failCount failed")
    }
}
