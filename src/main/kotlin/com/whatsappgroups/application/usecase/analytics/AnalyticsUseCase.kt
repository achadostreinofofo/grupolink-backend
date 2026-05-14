package com.whatsappgroups.application.usecase.analytics

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.domain.repository.GroupMemberRepository
import com.whatsappgroups.domain.repository.RedirectLogRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class AnalyticsUseCase(
    private val structureRepository: StructureRepository,
    private val groupRepository: WhatsappGroupRepository,
    private val redirectLogRepository: RedirectLogRepository,
    private val memberRepository: GroupMemberRepository,
    private val userRepository: UserRepository,
    @Value("\${app.base-url}") private val baseUrl: String
) {

    fun getStructureAnalytics(userId: UUID, structureId: UUID): StructureAnalyticsResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val since7d  = LocalDateTime.now().minusDays(7)
        val since30d = LocalDateTime.now().minusDays(30)

        val groups   = groupRepository.findAllByStructureOrderBySortOrderAsc(structure)
        val clicksByDay = redirectLogRepository.clicksPerDaySince(structure, since30d)
            .map { row -> DailyClick(row[0].toString(), (row[1] as Number).toLong()) }

        val totalClicks = redirectLogRepository.countByStructure(structure)

        val clicksLast7Days = redirectLogRepository.clicksPerDaySince(structure, since7d)
            .sumOf { (it[1] as Number).toLong() }

        val clicksByGroupSince30d = redirectLogRepository
            .countByGroupSince(structure, since30d)
            .associate { it[0].toString() to (it[1] as Number).toLong() }

        val groupAnalytics = groups.map { g ->
            GroupAnalytics(
                groupId             = g.id.toString(),
                groupName           = g.name,
                memberCount         = g.memberCount,
                maxMembers          = g.maxMembers,
                capacityPercentage  = g.capacityPercentage,
                clicksTotal         = clicksByGroupSince30d[g.id.toString()] ?: 0L,
                status              = g.status.name
            )
        }

        return StructureAnalyticsResponse(
            structureId   = structure.id.toString(),
            structureName = structure.name,
            smartLink     = "$baseUrl/r/${structure.slug}",
            totalClicks   = totalClicks,
            totalMembers  = groups.sumOf { it.memberCount },
            clicksLast7Days = clicksLast7Days,
            clicksByDay   = fillMissingDays(clicksByDay, 30),
            groups        = groupAnalytics
        )
    }

    fun getOverview(userId: UUID): OverviewAnalyticsResponse {
        val user       = userRepository.getReferenceById(userId)
        val structures = structureRepository.findAllByOwner(user)
        val since7d    = LocalDateTime.now().minusDays(7)
        val since30d   = LocalDateTime.now().minusDays(30)

        val allGroups  = structures.flatMap { s ->
            groupRepository.findAllByStructureOrderBySortOrderAsc(s)
        }

        val clicksByDay = redirectLogRepository.clicksPerDayByOwner(userId, since30d)
            .map { row -> DailyClick(row[0].toString(), (row[1] as Number).toLong()) }

        val totalClicks    = redirectLogRepository.clicksByStructureSince(userId, LocalDateTime.of(2020, 1, 1, 0, 0))
            .sumOf { (it[1] as Number).toLong() }

        val clicksLast7Days = redirectLogRepository.clicksPerDayByOwner(userId, since7d)
            .sumOf { (it[1] as Number).toLong() }

        return OverviewAnalyticsResponse(
            totalStructures = structures.size,
            totalGroups     = allGroups.size,
            totalMembers    = allGroups.sumOf { it.memberCount },
            totalClicks     = totalClicks,
            clicksLast7Days = clicksLast7Days,
            clicksByDay     = fillMissingDays(clicksByDay, 30)
        )
    }

    fun getChurnAnalytics(userId: UUID, structureId: UUID): ChurnAnalyticsResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val since30d = LocalDateTime.now().minusDays(30)

        val totalExits  = memberRepository.countChurnSince(structureId, since30d)
        val totalJoins  = memberRepository.countJoinsSince(structureId, since30d)
        val churnRate   = if (totalJoins > 0) totalExits.toDouble() / totalJoins.toDouble() else 0.0

        val exitsByDay  = memberRepository.churnPerDaySince(structureId, since30d)
            .map { row -> DailyExit(row[0].toString(), (row[1] as Number).toLong()) }
            .let { fillMissingExitDays(it, 30) }

        val exitsByGroup = memberRepository.churnByGroupSince(structureId, since30d)
            .map { row -> GroupChurn(row[0].toString(), row[1].toString(), (row[2] as Number).toLong()) }

        return ChurnAnalyticsResponse(
            structureId  = structureId.toString(),
            totalExits   = totalExits,
            totalJoins   = totalJoins,
            churnRate    = churnRate,
            exitsByDay   = exitsByDay,
            exitsByGroup = exitsByGroup
        )
    }

    fun getUtmAnalytics(userId: UUID, structureId: UUID, days: Int = 30): UtmAnalyticsResponse {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val since = LocalDateTime.now().minusDays(days.toLong())

        fun toEntries(rows: List<Array<Any>>): List<UtmEntry> {
            val total = rows.sumOf { (it[1] as Number).toLong() }.toDouble().coerceAtLeast(1.0)
            return rows.take(10).map { row ->
                val count = (row[1] as Number).toLong()
                UtmEntry(row[0].toString(), count, (count / total * 100).let { "%.1f".format(it).toDouble() })
            }
        }

        return UtmAnalyticsResponse(
            structureId = structureId.toString(),
            period      = "${days}d",
            bySources   = toEntries(redirectLogRepository.countByUtmSource(structureId, since)),
            byMedium    = toEntries(redirectLogRepository.countByUtmMedium(structureId, since)),
            byCampaign  = toEntries(redirectLogRepository.countByUtmCampaign(structureId, since))
        )
    }

    // Garante que todos os dias do período apareçam no gráfico, mesmo sem cliques
    private fun fillMissingDays(data: List<DailyClick>, days: Int): List<DailyClick> {
        val map = data.associate { it.date to it.clicks }
        return (days downTo 0).map { offset ->
            val date = LocalDate.now().minusDays(offset.toLong()).toString()
            DailyClick(date, map[date] ?: 0L)
        }
    }

    private fun fillMissingExitDays(data: List<DailyExit>, days: Int): List<DailyExit> {
        val map = data.associate { it.date to it.exits }
        return (days downTo 0).map { offset ->
            val date = LocalDate.now().minusDays(offset.toLong()).toString()
            DailyExit(date, map[date] ?: 0L)
        }
    }
}
