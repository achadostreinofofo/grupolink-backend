package com.whatsappgroups.infrastructure.messaging

import org.slf4j.LoggerFactory
import org.springframework.amqp.AmqpException
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.util.UUID

@Primary
@Component
class RabbitMQGroupEventPublisher(
    private val rabbitTemplate: RabbitTemplate
) : GroupEventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun publishCreationCheck(structureId: UUID) {
        try {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.GROUP_CREATION_EXCHANGE,
                RabbitMQConfig.GROUP_CREATION_KEY,
                GroupCreationMessage(structureId)
            )
        } catch (e: AmqpException) {
            // Se RabbitMQ estiver indisponível, registra mas não bloqueia o redirect
            log.warn("Falha ao publicar evento de criação de grupo para estrutura $structureId: ${e.message}")
        }
    }
}
