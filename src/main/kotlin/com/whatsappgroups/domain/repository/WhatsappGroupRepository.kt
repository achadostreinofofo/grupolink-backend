package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.WhatsappGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface WhatsappGroupRepository : JpaRepository<WhatsappGroup, UUID> {
    fun findAllByStructureOrderBySortOrderAsc(structure: Structure): List<WhatsappGroup>
    fun findAllByStructureAndStatusOrderBySortOrderAsc(structure: Structure, status: GroupStatus): List<WhatsappGroup>

    fun findAllByWhatsappGroupIdIsNotNull(): List<WhatsappGroup>

    @Modifying
    @Query("UPDATE WhatsappGroup g SET g.memberCount = g.memberCount + 1, g.clickCount = g.clickCount + 1, g.updatedAt = CURRENT_TIMESTAMP WHERE g.id = :id")
    fun incrementMemberAndClick(id: UUID)

    @Modifying
    @Query("UPDATE WhatsappGroup g SET g.clickCount = g.clickCount + 1, g.updatedAt = CURRENT_TIMESTAMP WHERE g.id = :id")
    fun incrementClick(id: UUID)
}
