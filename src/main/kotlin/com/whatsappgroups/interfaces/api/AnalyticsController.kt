package com.whatsappgroups.interfaces.api

import com.whatsappgroups.domain.repository.RedirectLogRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController(
    private val userRepository: UserRepository,
    private val structureRepository: StructureRepository,
    private val redirectLogRepository: RedirectLogRepository,
) {
    @GetMapping("/overview")
    fun getOverview(@AuthenticationPrincipal userDetails: UserDetails?): ResponseEntity<Any> {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Não autenticado"))
        }

        val userId = UUID.fromString(userDetails.username)
        val user = userRepository.findById(userId).orElse(null)
            ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to "Usuário não encontrado"))

        val structures = structureRepository.findAllByOwner(user)
        val totalGroups  = structures.sumOf { it.groups.size }
        val totalMembers = structures.sumOf { s -> s.groups.sumOf { g -> g.memberCount } }

        val since7d = LocalDateTime.now().minusDays(7)
        val since30d = LocalDateTime.now().minusDays(30)

        val clicksByDay7 = redirectLogRepository.clicksPerDayByOwner(userId, since7d)
        val totalClicks  = redirectLogRepository.clicksPerDayByOwner(userId, since30d)
            .sumOf { row -> (row[1] as Number).toLong() }

        val clicksLast7Days = clicksByDay7.sumOf { row -> (row[1] as Number).toLong() }

        // Preenche todos os 7 dias (inclusive dias sem cliques)
        val clickMap = clicksByDay7.associate { row ->
            row[0].toString() to (row[1] as Number).toLong()
        }
        val clicksByDayFilled = (6 downTo 0).map { daysAgo ->
            val date = LocalDate.now().minusDays(daysAgo.toLong()).toString()
            mapOf("date" to date, "clicks" to (clickMap[date] ?: 0L))
        }

        return ResponseEntity.ok(mapOf(
            "totalStructures"  to structures.size,
            "totalGroups"      to totalGroups,
            "totalMembers"     to totalMembers,
            "totalClicks"      to totalClicks,
            "clicksLast7Days"  to clicksLast7Days,
            "clicksByDay"      to clicksByDayFilled,
        ))
    }
}
