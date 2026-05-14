package com.whatsappgroups.application.usecase.group

import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.WhatsappGroup
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AutoCreateGroupUseCase(
    private val structureRepository: StructureRepository,
    private val groupRepository: WhatsappGroupRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /*
     * Acionado assincronamente pelo ProcessRedirectUseCase quando todos os grupos
     * ativos atingem o fillThreshold. Cria o próximo grupo com status CREATING,
     * sinalizando ao operador que precisa criar o grupo no WhatsApp e adicionar o link.
     *
     * Quando a Cloud API suportar criação de grupos, substituir por chamada real aqui.
     */
    @Transactional
    fun triggerIfNeeded(structureId: java.util.UUID) {
        val structure = structureRepository.findById(structureId).orElse(null) ?: return

        val activeGroups = groupRepository
            .findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE)

        // Só auto-cria se TODOS os grupos ativos estão acima do threshold
        val allAboveThreshold = activeGroups.isNotEmpty() &&
            activeGroups.all { it.capacityPercentage >= structure.fillThreshold }

        // Verifica se já existe um grupo em CREATING para esta estrutura
        val alreadyCreating = groupRepository
            .findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.CREATING)
            .isNotEmpty()

        if (!allAboveThreshold || alreadyCreating) return

        val nextOrder = (activeGroups.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val newGroup  = groupRepository.save(
            WhatsappGroup(
                structure  = structure,
                name       = "${structure.name} #${nextOrder + 1}",
                maxMembers = structure.maxMembersPerGroup,
                status     = GroupStatus.CREATING,
                sortOrder  = nextOrder
            )
        )

        log.info(
            "Auto-criação disparada para estrutura '${structure.slug}': " +
            "novo grupo '${newGroup.name}' (id=${newGroup.id}) aguarda link de convite"
        )
    }
}
