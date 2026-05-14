package com.whatsappgroups.infrastructure.messaging

import com.whatsappgroups.application.usecase.group.AutoCreateGroupUseCase
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class GroupCreationConsumer(private val autoCreateGroupUseCase: AutoCreateGroupUseCase) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [RabbitMQConfig.GROUP_CREATION_QUEUE])
    fun onGroupCreationCheck(message: GroupCreationMessage) {
        log.debug("Processando verificação de criação de grupo para estrutura ${message.structureId}")
        autoCreateGroupUseCase.triggerIfNeeded(message.structureId)
    }
}
