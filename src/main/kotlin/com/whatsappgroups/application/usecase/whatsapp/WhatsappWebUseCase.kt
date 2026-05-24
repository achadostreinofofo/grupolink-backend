package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.model.WhatsappWebSession
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class WhatsappWebUseCase(
    private val sessionRepository: WhatsappWebSessionRepository,
    private val userRepository: UserRepository,
    private val serviceClient: WhatsappWebServiceClient
) {

    @Transactional
    fun startSession(userId: UUID, force: Boolean = false): StartSessionResponse {
        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        // Reaproveita sessão já autenticada quando o cliente não pediu criação explícita.
        // Evita criar sessão órfã e disparar polling de QR quando o usuário só está
        // revisitando a tela de conexão.
        if (!force) {
            val existing = sessionRepository
                .findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED)
                .orElse(null)
            if (existing != null) {
                return StartSessionResponse(
                    sessionId = existing.sessionId,
                    status    = existing.status.name
                )
            }
        }

        // Remove sessões antigas WAITING_SCAN antes de criar nova
        sessionRepository.findAllByOwner(owner)
            .filter { it.status == WebSessionStatus.WAITING_SCAN }
            .forEach { stale ->
                serviceClient.deleteSession(stale.sessionId)
                sessionRepository.delete(stale)
            }

        // Cria uma nova sessão (caminho usado quando force=true ou não há sessão autenticada)
        val sessionId = UUID.randomUUID().toString()
        val session = sessionRepository.save(
            WhatsappWebSession(owner = owner, sessionId = sessionId)
        )

        val created = serviceClient.createSession(sessionId)
        if (!created) {
            sessionRepository.delete(session)
            throw IllegalStateException(
                "Não foi possível iniciar a sessão. Nosso serviço está temporariamente indisponível. " +
                "Por favor, tente novamente em alguns instantes."
            )
        }

        return StartSessionResponse(sessionId = session.sessionId, status = session.status.name)
    }

    @Transactional
    fun getSessionStatus(userId: UUID, sessionId: String): SessionStatusResponse {
        val owner = userRepository.getReferenceById(userId)
        val session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow { NoSuchElementException("Sessão não encontrada") }

        if (session.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val remote = serviceClient.getSessionStatus(sessionId)

        val newStatus = when (remote.status) {
            "authenticated"  -> WebSessionStatus.AUTHENTICATED
            "disconnected"   -> WebSessionStatus.DISCONNECTED
            "not_found"      -> {
                if (session.status == WebSessionStatus.WAITING_SCAN) {
                    serviceClient.createSession(sessionId)
                }
                WebSessionStatus.WAITING_SCAN
            }
            else             -> WebSessionStatus.WAITING_SCAN
        }

        if (session.status != newStatus || (newStatus == WebSessionStatus.AUTHENTICATED && session.phone != remote.phone)) {
            session.status    = newStatus
            session.phone     = remote.phone
            session.updatedAt = LocalDateTime.now()
        }

        return SessionStatusResponse(
            sessionId = sessionId,
            status    = newStatus.name,
            qrBase64  = remote.qrBase64,
            phone     = remote.phone ?: session.phone
        )
    }

    @Transactional
    fun disconnectSession(userId: UUID, sessionId: String) {
        val session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow { NoSuchElementException("Sessão não encontrada") }

        if (session.owner.id != userId) throw IllegalAccessException("Acesso negado")

        serviceClient.deleteSession(sessionId)
        // Deleta o registro — não apenas marca como DISCONNECTED
        sessionRepository.delete(session)
    }

    @Transactional
    fun reconnectSession(userId: UUID, sessionId: String): SessionStatusResponse {
        val session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow { NoSuchElementException("Sessão não encontrada") }

        if (session.owner.id != userId) throw IllegalAccessException("Acesso negado")

        // Tenta recriar o socket — se as credenciais ainda estão em disco, Baileys reconecta sem QR
        serviceClient.createSession(sessionId)
        session.status    = WebSessionStatus.WAITING_SCAN
        session.updatedAt = LocalDateTime.now()

        return SessionStatusResponse(
            sessionId = sessionId,
            status    = session.status.name,
            qrBase64  = null,
            phone     = session.phone
        )
    }

    fun listSessions(userId: UUID): List<SessionStatusResponse> {
        val owner = userRepository.getReferenceById(userId)
        // Retorna apenas sessões ativas (WAITING_SCAN ou AUTHENTICATED)
        return sessionRepository.findAllByOwner(owner)
            .filter { it.status != WebSessionStatus.DISCONNECTED }
            .map { s ->
                SessionStatusResponse(
                    sessionId = s.sessionId,
                    status    = s.status.name,
                    qrBase64  = null,
                    phone     = s.phone
                )
            }
    }

    fun getAuthenticatedSession(userId: UUID): WhatsappWebSession? {
        val owner = userRepository.getReferenceById(userId)
        return sessionRepository.findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED)
            .orElse(null)
    }
}
