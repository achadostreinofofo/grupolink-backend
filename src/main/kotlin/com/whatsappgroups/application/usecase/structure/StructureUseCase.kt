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
import com.whatsappgroups.infrastructure.whatsapp.WebServiceGroupDetail
import com.whatsappgroups.infrastructure.config.OwnerAccount
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
                         else owner.plan.maxStructures
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
    fun importGroups(userId: UUID, structureId: UUID, request: ImportGroupsRequest): ImportGroupsResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }

        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val groupIds = request.whatsappGroupIds.distinct()
        require(groupIds.isNotEmpty()) { "Selecione ao menos um grupo para importar." }

        // Respeita o limite de grupos por estrutura do plano
        val activeGroups = groupRepository
            .findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE)
        val maxGroups = if (OwnerAccount.isOwner(structure.owner.email)) Int.MAX_VALUE
                        else structure.owner.plan.maxGroupsPerStructure
        if (activeGroups.size + groupIds.size > maxGroups) {
            throw IllegalArgumentException(
                "Seu plano ${structure.owner.plan.name} permite no máximo $maxGroups grupo(s) por estrutura. " +
                "Esta estrutura já tem ${activeGroups.size} e você tentou importar ${groupIds.size}."
            )
        }

        val session = sessionRepository
            .findFirstByOwnerAndStatus(structure.owner, WebSessionStatus.AUTHENTICATED)
            .orElseThrow {
                IllegalStateException("Nenhuma sessão WhatsApp autenticada. Conecte uma conta em WhatsApp → QR Code.")
            }

        // 1. Busca info de cada grupo; falhas individuais não abortam o lote
        data class Resolved(val info: WebServiceGroupDetail, val inviteLink: String?)
        val resolved = mutableListOf<Resolved>()
        val failed   = mutableListOf<FailedImport>()
        for (gid in groupIds) {
            val info = whatsappWebClient.getGroupInfo(session.sessionId, gid)
            if (info == null) {
                failed.add(FailedImport(gid, "Não foi possível obter as informações do grupo."))
                continue
            }
            val link = info.inviteLink ?: whatsappWebClient.getGroupInviteLink(session.sessionId, gid)
            resolved.add(Resolved(info, link))
        }

        if (resolved.isEmpty()) {
            return ImportGroupsResponse(imported = emptyList(), failed = failed)
        }

        // 2. Limite de membros é único por estrutura e nunca menor que o grupo mais cheio importado
        val maxParticipants = resolved.maxOf { it.info.participants }
        val chosenMax       = request.maxMembersPerGroup ?: structure.maxMembersPerGroup
        structure.maxMembersPerGroup = maxOf(chosenMax, maxParticipants)
        request.fillThreshold?.let { threshold ->
            require(threshold in 0.10..0.99) { "fillThreshold deve ser entre 10% e 99%" }
            structure.fillThreshold = threshold
        }

        // 3. O primeiro grupo da estrutura define prefixo/foto usados na numeração futura
        if (structure.groupNamePrefix == null) {
            val first = resolved.first().info
            structure.groupNamePrefix    = first.name.substringBeforeLast(" #").trim()
            structure.nextGroupNumber    = 2
            structure.groupProfilePicUrl = first.profilePicUrl
        }

        // 4. Cria os grupos na ordem de seleção, gravando memberCount (essencial p/ round-robin
        //    e auto-criação: sem isso todos pareceriam vazios e a distribuição sairia errada)
        var order = structure.groups.size
        val imported = resolved.map { r ->
            val group = groupRepository.save(
                WhatsappGroup(
                    structure       = structure,
                    name            = r.info.name,
                    whatsappGroupId = r.info.groupId,
                    inviteLink      = r.inviteLink,
                    maxMembers      = structure.maxMembersPerGroup,
                    memberCount     = r.info.participants,
                    sortOrder       = order++,
                    status          = GroupStatus.ACTIVE
                )
            )
            connectedAccounts.addOtherAccountsToGroup(structure.owner, session.sessionId, r.info.groupId)
            log.info(
                "Grupo importado: '${group.name}' (jid=${r.info.groupId}, membros=${r.info.participants}, " +
                "maxMembers=${structure.maxMembersPerGroup})"
            )
            group.toResponse()
        }

        return ImportGroupsResponse(imported = imported, failed = failed)
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
