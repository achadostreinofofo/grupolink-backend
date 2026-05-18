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
    fun startSession(userId: UUID): StartSessionResponse {
        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        // Reuse active session if present
        val existing = sessionRepository.findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED)
        if (existing.isPresent) {
            val s = existing.get()
            return StartSessionResponse(sessionId = s.sessionId, status = s.status.name)
        }

        val sessionId = UUID.randomUUID().toString()
        val session = sessionRepository.save(
            WhatsappWebSession(owner = owner, sessionId = sessionId)
        )

        val created = serviceClient.createSession(sessionId)
        if (!created) {
            sessionRepository.delete(session)
            throw IllegalStateException(
                "WhatsApp Service não está disponível. " +
                "Inicie o whatsapp-service com: cd whatsapp-service && npm install && node src/index.js"
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

        // Sync status from WhatsApp service → DB
        val newStatus = when (remote.status) {
            "authenticated"  -> WebSessionStatus.AUTHENTICATED
            "disconnected"   -> WebSessionStatus.DISCONNECTED
            "not_found"      -> {
                // Session was lost (service restarted?), recreate it
                serviceClient.createSession(sessionId)
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
        session.status    = WebSessionStatus.DISCONNECTED
        session.updatedAt = LocalDateTime.now()
    }

    fun listSessions(userId: UUID): List<SessionStatusResponse> {
        val owner = userRepository.getReferenceById(userId)
        return sessionRepository.findAllByOwner(owner).map { s ->
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
