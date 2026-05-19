package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.model.WhatsappWebSession
import com.whatsappgroups.domain.model.WebSessionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface WhatsappWebSessionRepository : JpaRepository<WhatsappWebSession, UUID> {
    fun findBySessionId(sessionId: String): Optional<WhatsappWebSession>
    fun findAllByOwner(owner: User): List<WhatsappWebSession>
    fun findFirstByOwnerAndStatus(owner: User, status: WebSessionStatus): Optional<WhatsappWebSession>
    fun existsByOwnerAndStatus(owner: User, status: WebSessionStatus): Boolean
}
