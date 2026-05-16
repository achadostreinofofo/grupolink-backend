package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    fun findByEmailVerifyToken(token: String): User?

    @Query("SELECT u FROM User u WHERE u.plan = :plan AND u.trialEndsAt IS NOT NULL AND u.trialEndsAt < :now")
    fun findExpiredTrialUsers(plan: Plan, now: LocalDateTime): List<User>
}
