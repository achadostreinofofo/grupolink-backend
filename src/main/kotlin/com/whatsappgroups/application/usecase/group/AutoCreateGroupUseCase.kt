package com.whatsappgroups.application.usecase.group

import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.model.WhatsappGroup
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AutoCreateGroupUseCase(
    private val structureRepository: StructureRepository,
    private val groupRepository: WhatsappGroupRepository,
    private val sessionRepository: WhatsappWebSessionRepository,
    private val whatsappClient: WhatsappWebServiceClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun triggerIfNeeded(structureId: java.util.UUID) {
        val structure = structureRepository.findById(structureId).orElse(null) ?: return

        val activeGroups = groupRepository
            .findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE)

        val allAboveThreshold = activeGroups.isNotEmpty() &&
            activeGroups.all { it.capacityPercentage >= structure.fillThreshold }

        val alreadyCreating = groupRepository
            .findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.CREATING)
            .isNotEmpty()

        if (!allAboveThreshold || alreadyCreating) return

        val nextOrder = (activeGroups.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val groupName = "${structure.name} #${nextOrder + 1}"

        // Tenta criar o grupo diretamente no WhatsApp se o dono tiver sessão ativa
        val session = sessionRepository.findFirstByOwnerAndStatus(
            structure.owner, WebSessionStatus.AUTHENTICATED
        ).orElse(null)

        if (session != null) {
            runCatching {
                val result = whatsappClient.createGroup(
                    sessionId      = session.sessionId,
                    groupName      = groupName,
                    participants   = emptyList(),
                    profilePicUrl  = structure.groupProfilePicUrl
                )
                val newGroup = groupRepository.save(
                    WhatsappGroup(
                        structure       = structure,
                        name            = groupName,
                        maxMembers      = structure.maxMembersPerGroup,
                        status          = GroupStatus.ACTIVE,
                        sortOrder       = nextOrder,
                        whatsappGroupId = result.groupId,
                        inviteLink      = result.inviteLink
                    )
                )
                log.info(
                    "Grupo '${newGroup.name}' criado no WhatsApp " +
                    "(jid=${result.groupId}, link=${result.inviteLink})"
                )
            }.onFailure { e ->
                log.error("Falha ao criar grupo no WhatsApp para '${structure.slug}': ${e.message}")
                // Fallback: cria como CREATING para intervenção manual
                groupRepository.save(
                    WhatsappGroup(
                        structure  = structure,
                        name       = groupName,
                        maxMembers = structure.maxMembersPerGroup,
                        status     = GroupStatus.CREATING,
                        sortOrder  = nextOrder
                    )
                )
            }
        } else {
            // Sem sessão WhatsApp ativa — sinaliza para criação manual
            groupRepository.save(
                WhatsappGroup(
                    structure  = structure,
                    name       = groupName,
                    maxMembers = structure.maxMembersPerGroup,
                    status     = GroupStatus.CREATING,
                    sortOrder  = nextOrder
                )
            )
            log.warn(
                "Estrutura '${structure.slug}': nenhuma sessão WhatsApp ativa para o dono. " +
                "Grupo '${groupName}' criado com status CREATING — adicione o link manualmente."
            )
        }
    }
}
