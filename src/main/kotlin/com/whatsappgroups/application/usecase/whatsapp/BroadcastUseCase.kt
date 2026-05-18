package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.domain.model.*
import com.whatsappgroups.domain.repository.*
import com.whatsappgroups.infrastructure.messaging.BroadcastMessage
import com.whatsappgroups.infrastructure.messaging.RabbitMQConfig
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID

@Service
class BroadcastUseCase(
    private val broadcastRepository: MessageBroadcastRepository,
    private val broadcastResultRepository: BroadcastGroupResultRepository,
    private val structureRepository: StructureRepository,
    private val userRepository: UserRepository,
    private val rabbitTemplate: RabbitTemplate
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun broadcast(
        userId: UUID,
        structureId: UUID,
        request: BroadcastMessageRequest
    ): BroadcastMessageResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }

        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val activeGroups = structure.groups.filter { it.status == GroupStatus.ACTIVE }
        val targetGroups = if (request.groupIds.isNullOrEmpty()) {
            activeGroups
        } else {
            val ids = request.groupIds.map { UUID.fromString(it) }.toSet()
            activeGroups.filter { it.id in ids }
        }

        if (targetGroups.isEmpty()) throw IllegalArgumentException("Nenhum grupo ativo encontrado na estrutura")

        val messageType = runCatching { BroadcastMessageType.valueOf(request.messageType) }
            .getOrElse { BroadcastMessageType.TEXT }

        val broadcast = broadcastRepository.save(
            MessageBroadcast(
                userId      = userId,
                structure   = structure,
                messageType = messageType,
                content     = request.content,
                mediaUrl    = request.mediaUrl,
                status      = BroadcastStatus.QUEUED,
                totalGroups = targetGroups.size
            )
        )

        // Create pending result rows
        targetGroups.forEach { group ->
            broadcastResultRepository.save(
                BroadcastGroupResult(
                    broadcastId = broadcast.id!!,
                    group       = group
                )
            )
        }

        // Publica no RabbitMQ SOMENTE após o commit da transação
        // para evitar race condition onde o consumer lê o DB antes do commit
        val broadcastId = broadcast.id!!.toString()
        val userIdStr   = userId.toString()
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                rabbitTemplate.convertAndSend(
                    RabbitMQConfig.BROADCAST_EXCHANGE,
                    RabbitMQConfig.BROADCAST_KEY,
                    BroadcastMessage(broadcastId = broadcastId, userId = userIdStr)
                )
            }
        })

        log.info("Broadcast ${broadcast.id} queued for ${targetGroups.size} groups in structure $structureId")

        return BroadcastMessageResponse(
            broadcastId = broadcast.id!!.toString(),
            status      = broadcast.status.name,
            totalGroups = broadcast.totalGroups
        )
    }

    fun getStatus(userId: UUID, broadcastId: UUID): BroadcastStatusResponse {
        val broadcast = broadcastRepository.findById(broadcastId)
            .orElseThrow { NoSuchElementException("Broadcast não encontrado") }

        if (broadcast.userId != userId) throw IllegalAccessException("Acesso negado")

        return broadcast.toResponse()
    }

    fun listByUser(userId: UUID): List<BroadcastStatusResponse> =
        broadcastRepository.findAllByUserIdOrderByCreatedAtDesc(userId).map { it.toResponse() }

    private fun MessageBroadcast.toResponse() = BroadcastStatusResponse(
        broadcastId      = id.toString(),
        status           = status.name,
        totalGroups      = totalGroups,
        groupsProcessed  = groupsProcessed,
        groupsSuccessful = groupsSuccessful,
        groupsFailed     = groupsFailed,
        createdAt        = createdAt.toString()
    )
}
