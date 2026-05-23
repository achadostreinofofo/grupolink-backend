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
     * Lookup legado: sessão exata + grupo. Mantido para compatibilidade.
     */
    fun findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(
        sessionId: String,
        whatsappGroupId: String
    ): Optional<MonitoredGroup>

    /**
     * Lookup resiliente: encontra monitoramento ativo pelo dono + JID do grupo,
     * independente de qual sessão foi usada. Suporta troca de sessionId.
     */
    fun findFirstByOwnerAndWhatsappGroupIdAndActiveTrue(
        owner: User,
        whatsappGroupId: String
    ): Optional<MonitoredGroup>

    /**
     * Fallback: busca monitoramento apenas pelo JID do grupo quando a sessão
     * não está registrada no banco (ex: sessão criada diretamente no whatsapp-service).
     */
    fun findFirstByWhatsappGroupIdAndActiveTrue(
        whatsappGroupId: String
    ): Optional<MonitoredGroup>
}
