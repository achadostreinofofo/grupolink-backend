package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.AdminUser
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminUserRepository : JpaRepository<AdminUser, UUID> {
    fun findByEmail(email: String): AdminUser?
    fun existsByEmail(email: String): Boolean
}
