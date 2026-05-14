package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.ShortLink
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShortLinkRepository : JpaRepository<ShortLink, UUID> {
    fun findByCode(code: String): ShortLink?
    fun findAllByOwnerOrderByCreatedAtDesc(owner: User): List<ShortLink>
    fun existsByCode(code: String): Boolean
}
