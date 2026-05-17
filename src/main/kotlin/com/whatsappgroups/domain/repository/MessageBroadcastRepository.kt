package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.BroadcastStatus
import com.whatsappgroups.domain.model.MessageBroadcast
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageBroadcastRepository : JpaRepository<MessageBroadcast, UUID> {
    fun findAllByUserIdOrderByCreatedAtDesc(userId: UUID): List<MessageBroadcast>
    fun findAllByStatusIn(statuses: List<BroadcastStatus>): List<MessageBroadcast>
}
