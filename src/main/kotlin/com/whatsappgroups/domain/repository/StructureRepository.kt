package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StructureRepository : JpaRepository<Structure, UUID> {
    fun findBySlug(slug: String): Structure?
    fun findAllByOwner(owner: User): List<Structure>
    fun existsBySlug(slug: String): Boolean
}
