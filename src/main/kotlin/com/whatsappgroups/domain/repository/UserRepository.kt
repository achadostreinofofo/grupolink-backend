package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByCpf(cpf: String): Boolean
    fun findByEmailVerificationToken(token: String): User?
    fun findByPasswordResetToken(token: String): User?
}
