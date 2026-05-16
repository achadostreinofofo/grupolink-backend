package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.MessageStatus
import com.whatsappgroups.domain.model.ScheduledMessage
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.UUID

interface ScheduledMessageRepository : JpaRepository<ScheduledMessage, UUID> {
    fun findAllByOwner(owner: User): List<ScheduledMessage>
    fun countByOwnerAndStatus(owner: User, status: MessageStatus): Long
    fun findAllByStatusAndScheduledAtBefore(status: MessageStatus, dateTime: LocalDateTime): List<ScheduledMessage>
}
