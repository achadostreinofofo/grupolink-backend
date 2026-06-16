package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.model.WhatsappWebSession
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Centraliza o uso das múltiplas contas WhatsApp (celulares) conectadas de um usuário:
 * - adicionar as demais contas aos grupos (criados/importados);
 * - fornecer a lista de sessões para o rodízio de envio (anti-ban).
 */
@Service
class ConnectedAccountsService(
    private val sessionRepository: WhatsappWebSessionRepository,
    private val webServiceClient: WhatsappWebServiceClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Sessões autenticadas (celulares conectados) do usuário, ordenadas por criação. */
    fun authenticatedSessions(owner: User): List<WhatsappWebSession> =
        sessionRepository.findAllByOwner(owner)
            .filter { it.status == WebSessionStatus.AUTHENTICATED }
            .sortedBy { it.createdAt }

    /**
     * Adiciona ao grupo as demais contas conectadas do usuário (todas exceto a que criou/importou).
     * Best-effort: falhas (privacidade/sem admin/serviço fora) são logadas e não interrompem o fluxo.
     */
    fun addOtherAccountsToGroup(owner: User, creatorSessionId: String, whatsappGroupId: String) {
        val phones = authenticatedSessions(owner)
            .filter { it.sessionId != creatorSessionId }
            .mapNotNull { it.phone?.takeIf { p -> p.isNotBlank() } }
            .distinct()

        if (phones.isEmpty()) return

        runCatching { webServiceClient.addParticipants(creatorSessionId, whatsappGroupId, phones) }
            .onSuccess { ok ->
                if (ok) log.info("Contas adicionadas ao grupo $whatsappGroupId: ${phones.size}")
                else log.warn("Não foi possível adicionar todas as contas ao grupo $whatsappGroupId")
            }
            .onFailure { log.warn("Erro ao adicionar contas ao grupo $whatsappGroupId: ${it.message}") }
    }
}
