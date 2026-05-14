package com.whatsappgroups.infrastructure.messaging

import java.util.UUID

/*
 * Interface para publicar eventos de criação de grupo.
 * Implementações: RabbitMQ (produção) ou local @Async (fallback/dev).
 */
interface GroupEventPublisher {
    fun publishCreationCheck(structureId: UUID)
}
