package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.domain.model.MonitoredGroup
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.repository.MercadoLivreAccountRepository
import com.whatsappgroups.domain.repository.MonitoredGroupRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class MonitoredGroupUseCase(
    private val monitoredGroupRepository: MonitoredGroupRepository,
    private val webSessionRepository: WhatsappWebSessionRepository,
    private val structureRepository: StructureRepository,
    private val userRepository: UserRepository,
    private val webServiceClient: WhatsappWebServiceClient,
    private val broadcastUseCase: BroadcastUseCase,
    private val mlAccountRepository: MercadoLivreAccountRepository,
    private val mlAccountUseCase: MercadoLivreAccountUseCase,
    private val connectedAccounts: ConnectedAccountsService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // ── CRUD ──────────────────────────────────────────────────────

    @Transactional
    fun create(userId: UUID, request: CreateMonitoredGroupRequest): MonitoredGroupResponse {
        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val session = webSessionRepository.findBySessionId(request.sessionId)
            .orElseThrow { NoSuchElementException("Sessão WhatsApp não encontrada") }
        if (session.owner.id != userId) throw IllegalAccessException("Acesso negado")
        if (session.status != WebSessionStatus.AUTHENTICATED) {
            throw IllegalStateException("A sessão WhatsApp não está autenticada")
        }

        val structure = structureRepository.findById(UUID.fromString(request.structureId))
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val existing = monitoredGroupRepository
            .findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(
                request.sessionId, request.whatsappGroupId
            )
        if (existing.isPresent) {
            throw IllegalArgumentException("Este grupo já está sendo monitorado nessa sessão")
        }

        val saved = monitoredGroupRepository.save(
            MonitoredGroup(
                owner             = owner,
                webSession        = session,
                structure         = structure,
                whatsappGroupId   = request.whatsappGroupId,
                whatsappGroupName = request.whatsappGroupName,
                messagePrefix     = request.messagePrefix?.takeIf { it.isNotBlank() }
            )
        )

        return saved.toResponse()
    }

    @Transactional
    fun update(userId: UUID, id: UUID, request: UpdateMonitoredGroupRequest): MonitoredGroupResponse {
        val owner = userRepository.getReferenceById(userId)
        val monitored = monitoredGroupRepository.findByIdAndOwner(id, owner)
            .orElseThrow { NoSuchElementException("Grupo monitorado não encontrado") }

        request.structureId?.let { newId ->
            val structure = structureRepository.findById(UUID.fromString(newId))
                .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
            if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")
            monitored.structure = structure
        }
        request.messagePrefix?.let { monitored.messagePrefix = it.takeIf { p -> p.isNotBlank() } }
        request.active?.let { monitored.active = it }
        monitored.updatedAt = LocalDateTime.now()

        return monitored.toResponse()
    }

    @Transactional
    fun delete(userId: UUID, id: UUID) {
        val owner = userRepository.getReferenceById(userId)
        val monitored = monitoredGroupRepository.findByIdAndOwner(id, owner)
            .orElseThrow { NoSuchElementException("Grupo monitorado não encontrado") }
        monitoredGroupRepository.delete(monitored)
    }

    fun list(userId: UUID): List<MonitoredGroupResponse> {
        val owner = userRepository.getReferenceById(userId)
        return monitoredGroupRepository.findAllByOwner(owner)
            .sortedByDescending { it.createdAt }
            .map { it.toResponse() }
    }

    /**
     * Lista grupos da sessão WhatsApp Web, marcando quais já estão sendo monitorados.
     */
    fun listAvailableGroups(userId: UUID, sessionId: String): List<AvailableGroupResponse> {
        val session = webSessionRepository.findBySessionId(sessionId)
            .orElseThrow { NoSuchElementException("Sessão WhatsApp não encontrada") }
        if (session.owner.id != userId) throw IllegalAccessException("Acesso negado")
        if (session.status != WebSessionStatus.AUTHENTICATED) {
            throw IllegalStateException("A sessão WhatsApp não está autenticada")
        }

        val owner = userRepository.getReferenceById(userId)
        val monitoredIds = monitoredGroupRepository.findAllByOwner(owner)
            .filter { it.webSession.sessionId == sessionId }
            .map { it.whatsappGroupId }
            .toSet()

        return webServiceClient.listGroups(sessionId, onlyOwned = false).map { g ->
            AvailableGroupResponse(
                groupId          = g.groupId,
                name             = g.name,
                participants     = g.participants,
                alreadyMonitored = g.groupId in monitoredIds
            )
        }
    }

    // ── Webhook: processa mensagem capturada pelo whatsapp-service ─

    /**
     * Recebe uma mensagem já filtrada (contém https://meli.la/...) e redistribui
     * para todos os grupos ativos da estrutura vinculada. Nada é persistido.
     *
     * - Texto: usa BroadcastUseCase (via RabbitMQ).
     * - Imagem: envia base64 diretamente ao whatsapp-service por grupo, sem passar pelo banco.
     */
    @Transactional
    fun processIncomingMessage(payload: IncomingWhatsappMessageRequest) {
        // Tenta resolver a sessão pelo ID recebido no webhook
        val session = webSessionRepository.findBySessionId(payload.sessionId).orElse(null)

        // Busca o monitored group: por owner (se sessão conhecida) ou por groupId (fallback)
        val monitored: MonitoredGroup = if (session != null) {
            monitoredGroupRepository
                .findFirstByOwnerAndWhatsappGroupIdAndActiveTrue(session.owner, payload.groupId)
                .orElse(null)
        } else {
            // Sessão não está no banco (criada fora do fluxo normal) — busca só pelo groupId
            log.warn("Sessão ${payload.sessionId} não encontrada no banco — usando fallback por groupId")
            monitoredGroupRepository
                .findFirstByWhatsappGroupIdAndActiveTrue(payload.groupId)
                .orElse(null)
        } ?: run {
            log.debug("Mensagem de grupo não monitorado: group=${payload.groupId}")
            return
        }

        // Auto-repara referência de sessão quando ela foi recriada ou é nova
        if (monitored.webSession.sessionId != payload.sessionId) {
            val newSession = session ?: webSessionRepository.findBySessionId(payload.sessionId).orElse(null)
            if (newSession != null) {
                log.info(
                    "Sessão do monitored group atualizada: ${monitored.webSession.sessionId} → ${payload.sessionId} (group=${payload.groupId})"
                )
                monitored.webSession = newSession
                monitored.updatedAt  = LocalDateTime.now()
            }
        }

        val mlAccount = mlAccountRepository.findByOwner(monitored.owner).orElse(null)
        if (mlAccount == null) {
            log.warn("Usuário ${monitored.owner.id} não tem conta ML conectada — mensagem descartada")
            return
        }

        val ownerId     = monitored.owner.id ?: return
        val structure   = monitored.structure
        val structureId = structure.id ?: return
        val sessionId   = payload.sessionId   // usa a sessão ativa, não a salva no banco

        val textWithReplacedLinks = mlAccountUseCase.resolveAndReplaceLinks(payload.text, mlAccount)

        val finalContent = buildString {
            monitored.messagePrefix?.takeIf { it.isNotBlank() }?.let {
                append(it)
                if (!it.endsWith("\n") && !it.endsWith(" ")) append(' ')
            }
            append(textWithReplacedLinks)
        }

        log.info("Redistribuindo para estrutura '${structure.name}' (id=$structureId) — hasImage=${payload.imageBase64 != null}")

        if (payload.imageBase64 != null) {
            // Imagem: envia diretamente por grupo sem persistir nada
            val activeGroups = structure.groups.filter {
                it.status == com.whatsappgroups.domain.model.GroupStatus.ACTIVE &&
                it.whatsappGroupId != null
            }
            if (activeGroups.isEmpty()) {
                log.warn("Nenhum grupo ativo com whatsappGroupId na estrutura $structureId")
                return
            }
            // Rodízio de contas (anti-ban): cada grupo usa a próxima conta conectada;
            // a sessão que capturou a mensagem é o fallback.
            val rotation = connectedAccounts.authenticatedSessions(monitored.owner).map { it.sessionId }
                .ifEmpty { listOf(sessionId) }
            activeGroups.forEachIndexed { idx, group ->
                val chosen = rotation[idx % rotation.size]
                val ok = webServiceClient.sendImageBase64Message(
                    sessionId        = chosen,
                    whatsappGroupId  = group.whatsappGroupId!!,
                    imageBase64      = payload.imageBase64,
                    caption          = finalContent
                )
                if (!ok && chosen != sessionId) {
                    webServiceClient.sendImageBase64Message(sessionId, group.whatsappGroupId!!, payload.imageBase64, finalContent)
                }
            }
        } else {
            // Texto: usa o broadcast queue normal
            try {
                broadcastUseCase.broadcast(
                    userId      = ownerId,
                    structureId = structureId,
                    request     = BroadcastMessageRequest(
                        messageType = "TEXT",
                        content     = finalContent,
                        mediaUrl    = null,
                        groupIds    = null
                    )
                )
            } catch (ex: Exception) {
                log.error("Falha ao redistribuir texto para estrutura $structureId: ${ex.message}", ex)
            }
        }
    }

    // ── Mapper ────────────────────────────────────────────────────

    private fun MonitoredGroup.toResponse() = MonitoredGroupResponse(
        id                = id.toString(),
        sessionId         = webSession.sessionId,
        whatsappGroupId   = whatsappGroupId,
        whatsappGroupName = whatsappGroupName,
        structureId       = structure.id.toString(),
        structureName     = structure.name,
        messagePrefix     = messagePrefix,
        active            = active,
        createdAt         = createdAt.toString()
    )
}
