package com.whatsappgroups.application.usecase.structure

import com.whatsappgroups.application.dto.GroupResponse
import com.whatsappgroups.application.dto.PendingActionResponse
import com.whatsappgroups.application.dto.UpdateGroupRequest
import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class GroupUpdateUseCase(
    private val groupRepository: WhatsappGroupRepository,
    private val structureRepository: StructureRepository,
    private val userRepository: UserRepository
) {

    fun getPendingActions(userId: UUID): List<PendingActionResponse> {
        val owner      = userRepository.getReferenceById(userId)
        val structures = structureRepository.findAllByOwner(owner)

        return structures.flatMap { s ->
            groupRepository
                .findAllByStructureAndStatusOrderBySortOrderAsc(s, GroupStatus.CREATING)
                .map { g ->
                    PendingActionResponse(
                        groupId       = g.id.toString(),
                        groupName     = g.name,
                        structureId   = s.id.toString(),
                        structureName = s.name,
                        structureSlug = s.slug,
                        sortOrder     = g.sortOrder,
                        createdAt     = g.createdAt.toString()
                    )
                }
        }.sortedBy { it.createdAt }
    }

    @Transactional
    fun updateGroup(userId: UUID, structureId: UUID, groupId: UUID, request: UpdateGroupRequest): GroupResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val group = groupRepository.findById(groupId)
            .orElseThrow { NoSuchElementException("Grupo não encontrado") }
        if (group.structure.id != structureId) throw IllegalArgumentException("Grupo não pertence a esta estrutura")

        request.name?.let       { group.name       = it }
        request.maxMembers?.let { group.maxMembers  = it }
        request.inviteLink?.let {
            group.inviteLink = it
            // Ativar automaticamente quando o link de convite é adicionado
            if (it.isNotBlank() && group.status == GroupStatus.CREATING) {
                group.status = GroupStatus.ACTIVE
            }
        }
        group.updatedAt = LocalDateTime.now()

        return GroupResponse(
            id                 = group.id.toString(),
            name               = group.name,
            inviteLink         = group.inviteLink,
            memberCount        = group.memberCount,
            maxMembers         = group.maxMembers,
            capacityPercentage = group.capacityPercentage,
            clickCount         = group.clickCount,
            status             = group.status.name,
            sortOrder          = group.sortOrder,
            whatsappGroupId    = group.id.toString(),
        )
    }
}
