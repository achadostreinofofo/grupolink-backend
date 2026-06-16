package com.whatsappgroups.application.usecase.structure

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.WhatsappGroup
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.application.usecase.whatsapp.ConnectedAccountsService
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StructureUseCase(
    private val structureRepository: StructureRepository,
    private val groupRepository: WhatsappGroupRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: WhatsappWebSessionRepository,
    private val whatsappWebClient: WhatsappWebServiceClient,
    private val connectedAccounts: ConnectedAccountsService,
    @Value("\${app.base-url}") private val baseUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(userId: UUID, request: CreateStructureRequest): StructureResponse {
        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val existing = structureRepository.findAllByOwner(owner).size
        val maxAllowed = if (com.whatsappgroups.infrastructure.config.OwnerAccount.isOwner(owner.email)) Int.MAX_VALUE
        else when (owner.plan) {
            com.whatsappgroups.domain.model.Plan.FREE    -> 1
            com.whatsappgroups.domain.model.Plan.SMART   -> 1
            com.whatsappgroups.domain.model.Plan.DIAMOND -> 4
            com.whatsappgroups.domain.model.Plan.BLACK,
            com.whatsappgroups.domain.model.Plan.MAXIMUS -> Int.MAX_VALUE
        }
        if (existing >= maxAllowed) {
            throw IllegalArgumentException(
                "Seu plano ${owner.plan.name} permite no máximo $maxAllowed estrutura(s). " +
                "Faça upgrade para adicionar mais."
            )
        }

        val slug = generateUniqueSlug()
        val structure = structureRepository.save(
            Structure(
                owner = owner,
                name = request.name,
                slug = slug,
                description = request.description,
                maxMembersPerGroup = request.maxMembersPerGroup,
                fillThreshold = request.fillThreshold
            )
        )

        return structure.toResponse()
    }

    private fun generateUniqueSlug(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        var slug: String
        do { slug = (1..8).map { chars.random() }.joinToString("") }
        while (structureRepository.existsBySlug(slug))
        return slug
    }

    @Transactional
    fun delete(userId: UUID, structureId: UUID) {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")
        structureRepository.delete(structure)
    }

    fun listByUser(userId: UUID): List<StructureResponse> {
        val owner = userRepository.getReferenceById(userId)
        return structureRepository.findAllByOwner(owner).map { it.toResponse() }
    }

    fun getById(userId: UUID, structureId: UUID): StructureResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }

        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")
        return structure.toResponse()
    }

    @Transactional
    fun addGroup(userId: UUID, structureId: UUID, request: AddGroupRequest): GroupResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }

        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        // Apenas o primeiro grupo pode ser adicionado manualmente
        val hasActiveGroups = groupRepository
            .findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE)
            .isNotEmpty()
        if (hasActiveGroups) throw IllegalStateException(
            "Apenas o primeiro grupo pode ser criado manualmente. " +
            "Os grupos seguintes são criados automaticamente quando o atual atingir a capacidade."
        )

        val isFirstGroup = structure.groupNamePrefix == null

        // Primeiro grupo: salva prefixo, número inicial e foto de perfil
        if (isFirstGroup) {
            if (request.profilePicUrl.isNullOrBlank()) {
                throw IllegalArgumentException("A foto de perfil é obrigatória para o primeiro grupo da estrutura.")
            }
            structure.groupNamePrefix   = request.name
            structure.nextGroupNumber   = request.startingNumber + 1
            structure.groupProfilePicUrl = request.profilePicUrl
        }

        // Nome completo: "Achados Treino Fofo #10"
        val prefix = structure.groupNamePrefix ?: request.name
        val number = if (isFirstGroup) request.startingNumber else structure.nextGroupNumber
        val fullName = "$prefix #$number"

        // Incrementa contador para o próximo grupo
        if (!isFirstGroup) structure.nextGroupNumber = number + 1

        var whatsappGroupId: String? = null
        var inviteLink: String? = null
        var groupStatus = GroupStatus.CREATING

        // Cria grupo real no WhatsApp via whatsapp-service (se houver participantes)
        if (!request.participantJids.isNullOrEmpty()) {
            val session = sessionRepository
                .findFirstByOwnerAndStatus(structure.owner, WebSessionStatus.AUTHENTICATED)
                .orElseThrow {
                    IllegalStateException(
                        "Nenhuma sessão WhatsApp autenticada. Conecte uma conta em WhatsApp → QR Code."
                    )
                }

            // Verifica o estado real da sessão no whatsapp-service antes de criar o grupo
            val sessionStatus = whatsappWebClient.getSessionStatus(session.sessionId)
            when (sessionStatus.status) {
                "authenticated" -> {
                    // Sessão pronta — cria o grupo
                    try {
                        val result = whatsappWebClient.createGroup(
                            sessionId     = session.sessionId,
                            groupName     = fullName,
                            participants  = request.participantJids,
                            profilePicUrl = structure.groupProfilePicUrl
                        )
                        whatsappGroupId = result.groupId
                        inviteLink      = result.inviteLink
                        groupStatus     = GroupStatus.ACTIVE
                        log.info("WhatsApp group created: {} → {}", fullName, result.groupId)
                        // Adiciona as demais contas conectadas do usuário ao grupo
                        connectedAccounts.addOtherAccountsToGroup(structure.owner, session.sessionId, result.groupId)
                    } catch (e: Exception) {
                        log.error("createGroup error: ${e.message}")
                        throw IllegalStateException("Falha ao criar grupo no WhatsApp: ${e.message}")
                    }
                }
                "waiting_scan" -> {
                    // Já reconectando — não destruir o socket, só pedir para aguardar
                    throw IllegalStateException(
                        "A sessão WhatsApp está reconectando. Aguarde alguns segundos e tente novamente."
                    )
                }
                else -> {
                    // Sessão não existe no serviço — recria usando credenciais em disco
                    whatsappWebClient.createSession(session.sessionId)
                    throw IllegalStateException(
                        "Sessão WhatsApp foi reiniciada. Aguarde 5 segundos e tente criar o grupo novamente."
                    )
                }
            }
        }

        val group = groupRepository.save(
            WhatsappGroup(
                structure       = structure,
                name            = fullName,
                whatsappGroupId = whatsappGroupId,
                inviteLink      = inviteLink,
                maxMembers      = structure.maxMembersPerGroup,
                sortOrder       = structure.groups.size,
                status          = groupStatus
            )
        )

        return group.toResponse()
    }

    @Transactional
    fun importGroup(userId: UUID, structureId: UUID, request: ImportGroupRequest): GroupResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }

        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        // Apenas o primeiro grupo pode ser importado manualmente
        val hasActiveGroups = groupRepository
            .findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE)
            .isNotEmpty()
        if (hasActiveGroups) throw IllegalStateException(
            "Apenas o primeiro grupo pode ser importado manualmente. " +
            "Os grupos seguintes são criados automaticamente."
        )

        val session = sessionRepository
            .findFirstByOwnerAndStatus(structure.owner, WebSessionStatus.AUTHENTICATED)
            .orElseThrow {
                IllegalStateException("Nenhuma sessão WhatsApp autenticada. Conecte uma conta em WhatsApp → QR Code.")
            }

        // Busca info e invite link do grupo via whatsapp-service
        val groupInfo = whatsappWebClient.getGroupInfo(session.sessionId, request.whatsappGroupId)
            ?: throw IllegalStateException("Não foi possível obter informações do grupo '${request.whatsappGroupId}'. Verifique se a sessão WhatsApp está ativa.")
        val inviteLink = request.inviteLink
            ?: groupInfo.inviteLink
            ?: whatsappWebClient.getGroupInviteLink(session.sessionId, request.whatsappGroupId)

        // Aplica configurações do usuário (se fornecidas) na estrutura
        request.maxMembersPerGroup?.let { max ->
            require(max in groupInfo.participants..1024) {
                "maxMembersPerGroup deve ser entre ${groupInfo.participants} (participantes atuais) e 1024"
            }
            structure.maxMembersPerGroup = max
        }
        request.fillThreshold?.let { threshold ->
            require(threshold in 0.10..0.99) { "fillThreshold deve ser entre 10% e 99%" }
            structure.fillThreshold = threshold
        }

        val isFirstGroup = structure.groupNamePrefix == null

        if (isFirstGroup) {
            structure.groupNamePrefix    = groupInfo.name.substringBeforeLast(" #").trim()
            structure.nextGroupNumber    = 2
            structure.groupProfilePicUrl = groupInfo.profilePicUrl
        }

        val group = groupRepository.save(
            WhatsappGroup(
                structure       = structure,
                name            = groupInfo.name,
                whatsappGroupId = request.whatsappGroupId,
                inviteLink      = inviteLink,
                maxMembers      = structure.maxMembersPerGroup,
                sortOrder       = structure.groups.size,
                status          = GroupStatus.ACTIVE
            )
        )

        log.info(
            "Grupo importado: '${group.name}' (jid=${request.whatsappGroupId}, " +
            "maxMembers=${structure.maxMembersPerGroup}, fillThreshold=${structure.fillThreshold})"
        )
        // Adiciona as demais contas conectadas do usuário ao grupo importado
        connectedAccounts.addOtherAccountsToGroup(structure.owner, session.sessionId, request.whatsappGroupId)
        return group.toResponse()
    }

    private fun Structure.toResponse() = StructureResponse(
        id                 = id.toString(),
        name               = name,
        slug               = slug,
        description        = description,
        maxMembersPerGroup = maxMembersPerGroup,
        fillThreshold      = fillThreshold,
        active             = active,
        groups             = groups.map { it.toResponse() },
        smartLink          = "$baseUrl/r/$slug",
        groupNamePrefix    = groupNamePrefix,
        nextGroupNumber    = nextGroupNumber,
        groupProfilePicUrl = groupProfilePicUrl
    )

    private fun WhatsappGroup.toResponse() = GroupResponse(
        id              = id.toString(),
        name            = name,
        inviteLink      = inviteLink,
        memberCount     = memberCount,
        maxMembers      = maxMembers,
        capacityPercentage = capacityPercentage,
        clickCount      = clickCount,
        status          = status.name,
        sortOrder       = sortOrder,
        whatsappGroupId = whatsappGroupId
    )
}
