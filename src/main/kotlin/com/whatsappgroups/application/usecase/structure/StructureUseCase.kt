package com.whatsappgroups.application.usecase.structure

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.WhatsappGroup
import com.whatsappgroups.domain.model.limits
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class StructureUseCase(
    private val structureRepository: StructureRepository,
    private val groupRepository: WhatsappGroupRepository,
    private val userRepository: UserRepository,
    @Value("\${app.base-url}") private val baseUrl: String
) {

    @Transactional
    fun create(userId: UUID, request: CreateStructureRequest): StructureResponse {
        if (structureRepository.existsBySlug(request.slug)) {
            throw IllegalArgumentException("Slug '${request.slug}' já está em uso")
        }

        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val limits = owner.plan.limits()
        val currentCount = structureRepository.countByOwner(owner)
        if (currentCount >= limits.maxStructures) {
            throw IllegalArgumentException(
                "Seu plano ${owner.plan.name} permite no máximo ${limits.maxStructures} estrutura(s). " +
                "Faça upgrade para criar mais."
            )
        }
        val structure = structureRepository.save(
            Structure(
                owner = owner,
                name = request.name,
                slug = request.slug,
                description = request.description,
                maxMembersPerGroup = request.maxMembersPerGroup,
                fillThreshold = request.fillThreshold
            )
        )

        return structure.toResponse()
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

        val sortOrder = structure.groups.size
        val group = groupRepository.save(
            WhatsappGroup(
                structure = structure,
                name = request.name,
                inviteLink = request.inviteLink,
                maxMembers = request.maxMembers,
                sortOrder = sortOrder
            )
        )

        return group.toResponse()
    }

    private fun Structure.toResponse() = StructureResponse(
        id = id.toString(),
        name = name,
        slug = slug,
        description = description,
        maxMembersPerGroup = maxMembersPerGroup,
        fillThreshold = fillThreshold,
        active = active,
        groups = groups.map { it.toResponse() },
        smartLink = "$baseUrl/r/$slug"
    )

    private fun WhatsappGroup.toResponse() = GroupResponse(
        id = id.toString(),
        name = name,
        inviteLink = inviteLink,
        memberCount = memberCount,
        maxMembers = maxMembers,
        capacityPercentage = capacityPercentage,
        clickCount = clickCount,
        status = status.name,
        sortOrder = sortOrder
    )
}
