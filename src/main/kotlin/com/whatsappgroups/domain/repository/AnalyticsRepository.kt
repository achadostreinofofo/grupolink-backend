package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface AnalyticsRepository : JpaRepository<User, UUID> {
    @Query(value = """
        SELECT plan, COUNT(*) as count
        FROM users
        WHERE status = 'ACTIVE'
        GROUP BY plan
    """, nativeQuery = true)
    fun countUsersByPlan(): List<Map<String, Any>>

    @Query(value = """
        SELECT COUNT(*) as count
        FROM users
        WHERE status = 'ACTIVE'
    """, nativeQuery = true)
    fun countTotalActiveUsers(): Long

    @Query(value = """
        SELECT COUNT(*) as count
        FROM subscriptions
        WHERE status = 'ACTIVE'
        AND period_end_date > NOW()
    """, nativeQuery = true)
    fun countActiveSubscriptions(): Long

    @Query(value = """
        SELECT COALESCE(SUM(amount), 0) as mrr
        FROM subscriptions
        WHERE status = 'ACTIVE'
        AND period_end_date > NOW()
    """, nativeQuery = true)
    fun calculateMRR(): BigDecimal

    @Query(value = """
        SELECT COUNT(*) as count
        FROM users
        WHERE status = 'ACTIVE'
        AND created_at > NOW() - INTERVAL '7 days'
    """, nativeQuery = true)
    fun countNewUsersLast7Days(): Long

    @Query(value = """
        SELECT COUNT(*) as count
        FROM users
        WHERE status = 'ACTIVE'
        AND created_at > NOW() - INTERVAL '30 days'
    """, nativeQuery = true)
    fun countNewUsersLast30Days(): Long

    @Query(value = """
        SELECT COUNT(*) as count
        FROM users u
        WHERE u.status = 'ACTIVE'
        AND (SELECT MAX(updated_at) FROM structures WHERE owner_id = u.id) < NOW() - INTERVAL '30 days'
        OR NOT EXISTS (SELECT 1 FROM structures WHERE owner_id = u.id)
    """, nativeQuery = true)
    fun countInactiveUsers30Days(): Long

    @Query(value = """
        SELECT COALESCE(SUM(s.amount), 0) / NULLIF(COUNT(DISTINCT s.owner_id), 0) as arpu
        FROM subscriptions s
        WHERE s.status = 'ACTIVE'
        AND s.period_end_date > NOW()
    """, nativeQuery = true)
    fun calculateARPU(): BigDecimal
}
