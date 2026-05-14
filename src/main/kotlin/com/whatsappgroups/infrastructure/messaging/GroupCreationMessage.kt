package com.whatsappgroups.infrastructure.messaging

import java.time.LocalDateTime
import java.util.UUID

data class GroupCreationMessage(
    val structureId: UUID,
    val requestedAt: LocalDateTime = LocalDateTime.now()
)
