package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.MonitoredGroup
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface MonitoredGroupRepository : JpaRepository<MonitoredGroup, UUID> {

    fun findAllByOwner(owner: User): List<MonitoredGroup>

    fun findByIdAndOwner(id: UUID, owner: User): Optional<MonitoredGroup>

    /**
     * Lookup principal usado pelo webhook: dado um sessionId do WhatsApp Service
     * e o JID do grupo, retorna a configuração ativa (se houver).
     */
    fun findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(
        sessionId: String,
        whatsappGroupId: String
    ): Optional<MonitoredGroup>
}
