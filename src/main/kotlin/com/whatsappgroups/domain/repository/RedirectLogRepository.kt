package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.RedirectLog
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.WhatsappGroup
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime
import java.util.UUID

interface RedirectLogRepository : JpaRepository<RedirectLog, UUID> {
    fun findByCookieIdAndStructure(cookieId: String, structure: Structure): RedirectLog?

    fun countByGroupAndRedirectedAtBetween(
        group: WhatsappGroup,
        start: LocalDateTime,
        end: LocalDateTime
    ): Long

    @Query("""
        SELECT r.group.id as groupId, COUNT(r) as total
        FROM RedirectLog r
        WHERE r.structure = :structure AND r.redirectedAt >= :since
        GROUP BY r.group.id
        ORDER BY total DESC
    """)
    fun countByGroupSince(structure: Structure, since: LocalDateTime): List<Array<Any>>

    // Cliques agrupados por dia nos últimos N dias para uma estrutura
    @Query("""
        SELECT CAST(r.redirectedAt AS date) as day, COUNT(r) as clicks
        FROM RedirectLog r
        WHERE r.structure = :structure AND r.redirectedAt >= :since
        GROUP BY CAST(r.redirectedAt AS date)
        ORDER BY day ASC
    """)
    fun clicksPerDaySince(structure: Structure, since: LocalDateTime): List<Array<Any>>

    // Cliques totais por estrutura de um usuário nos últimos N dias
    @Query("""
        SELECT r.structure.id as structureId, COUNT(r) as total
        FROM RedirectLog r
        WHERE r.structure.owner.id = :ownerId AND r.redirectedAt >= :since
        GROUP BY r.structure.id
    """)
    fun clicksByStructureSince(ownerId: UUID, since: LocalDateTime): List<Array<Any>>

    // Overview: cliques por dia de todas as estruturas do usuário
    @Query("""
        SELECT CAST(r.redirectedAt AS date) as day, COUNT(r) as clicks
        FROM RedirectLog r
        WHERE r.structure.owner.id = :ownerId AND r.redirectedAt >= :since
        GROUP BY CAST(r.redirectedAt AS date)
        ORDER BY day ASC
    """)
    fun clicksPerDayByOwner(ownerId: UUID, since: LocalDateTime): List<Array<Any>>

    fun countByStructure(structure: Structure): Long

    @Query("SELECT r FROM RedirectLog r WHERE r.structure.id = :structureId ORDER BY r.redirectedAt DESC")
    fun findAllByStructureIdWithDetails(structureId: UUID): List<RedirectLog>

    @Query("""
        SELECT r.utmSource as label, COUNT(r) as total
        FROM RedirectLog r
        WHERE r.structure.id = :structureId AND r.redirectedAt >= :since
          AND r.utmSource IS NOT NULL
        GROUP BY r.utmSource ORDER BY total DESC
    """)
    fun countByUtmSource(structureId: UUID, since: LocalDateTime): List<Array<Any>>

    @Query("""
        SELECT r.utmMedium as label, COUNT(r) as total
        FROM RedirectLog r
        WHERE r.structure.id = :structureId AND r.redirectedAt >= :since
          AND r.utmMedium IS NOT NULL
        GROUP BY r.utmMedium ORDER BY total DESC
    """)
    fun countByUtmMedium(structureId: UUID, since: LocalDateTime): List<Array<Any>>

    @Query("""
        SELECT r.utmCampaign as label, COUNT(r) as total
        FROM RedirectLog r
        WHERE r.structure.id = :structureId AND r.redirectedAt >= :since
          AND r.utmCampaign IS NOT NULL
        GROUP BY r.utmCampaign ORDER BY total DESC
    """)
    fun countByUtmCampaign(structureId: UUID, since: LocalDateTime): List<Array<Any>>
}
