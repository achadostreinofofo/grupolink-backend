package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.GroupMember
import com.whatsappgroups.domain.model.WhatsappGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface GroupMemberRepository : JpaRepository<GroupMember, UUID> {

    fun findAllByGroupAndLeftAtIsNull(group: WhatsappGroup): List<GroupMember>

    fun findByGroupAndPhoneNumber(group: WhatsappGroup, phoneNumber: String): GroupMember?

    fun findByGroupAndCookieId(group: WhatsappGroup, cookieId: String): GroupMember?

    fun countByGroupAndLeftAtIsNull(group: WhatsappGroup): Long

    // Todos os membros ativos de uma estrutura (para exportação)
    @Query("""
        SELECT m FROM GroupMember m
        WHERE m.group.structure.id = :structureId
        ORDER BY m.joinedAt DESC
    """)
    fun findAllByStructureId(structureId: UUID): List<GroupMember>

    // Total de saídas num período
    @Query("""
        SELECT COUNT(m) FROM GroupMember m
        WHERE m.group.structure.id = :structureId
          AND m.leftAt IS NOT NULL AND m.leftAt >= :since
    """)
    fun countChurnSince(structureId: UUID, since: java.time.LocalDateTime): Long

    // Saídas agrupadas por dia (churn time-series)
    @Query("""
        SELECT CAST(m.leftAt AS date) as day, COUNT(m) as exits
        FROM GroupMember m
        WHERE m.group.structure.id = :structureId
          AND m.leftAt IS NOT NULL AND m.leftAt >= :since
        GROUP BY CAST(m.leftAt AS date)
        ORDER BY day ASC
    """)
    fun churnPerDaySince(structureId: UUID, since: java.time.LocalDateTime): List<Array<Any>>

    // Saídas por grupo (para ranking)
    @Query("""
        SELECT m.group.id as groupId, m.group.name as groupName, COUNT(m) as exits
        FROM GroupMember m
        WHERE m.group.structure.id = :structureId
          AND m.leftAt IS NOT NULL AND m.leftAt >= :since
        GROUP BY m.group.id, m.group.name
        ORDER BY exits DESC
    """)
    fun churnByGroupSince(structureId: UUID, since: java.time.LocalDateTime): List<Array<Any>>

    // Total de entradas num período
    @Query("""
        SELECT COUNT(m) FROM GroupMember m
        WHERE m.group.structure.id = :structureId AND m.joinedAt >= :since
    """)
    fun countJoinsSince(structureId: UUID, since: java.time.LocalDateTime): Long
}
