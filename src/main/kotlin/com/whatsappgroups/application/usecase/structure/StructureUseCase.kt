package com.whatsappgroups.application.usecase.structure

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.WhatsappGroup
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
        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val existing = structureRepository.findAllByOwner(owner).size
        val maxAllowed = when (owner.plan) {
            com.whatsappgroups.domain.model.Plan.FREE    -> 1
            com.whatsappgroups.domain.model.Plan.SMART   -> 1
            com.whatsappgroups.domain.model.Plan.DIAMOND -> 4
            com.whatsappgroups.domain.model.Plan.BLACK   -> Int.MAX_VALUE
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

        val group = groupRepository.save(
            WhatsappGroup(
                structure = structure,
                name      = fullName,
                maxMembers = structure.maxMembersPerGroup,
                sortOrder  = structure.groups.size
            )
        )

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
