package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.ChurnAnalyticsResponse
import com.whatsappgroups.application.dto.OverviewAnalyticsResponse
import com.whatsappgroups.application.dto.StructureAnalyticsResponse
import com.whatsappgroups.application.dto.UtmAnalyticsResponse
import com.whatsappgroups.application.usecase.analytics.AnalyticsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/analytics")
class AnalyticsController(private val analyticsUseCase: AnalyticsUseCase) {

    @GetMapping("/overview")
    fun overview(@AuthenticationPrincipal user: UserDetails): ResponseEntity<OverviewAnalyticsResponse> =
        ResponseEntity.ok(analyticsUseCase.getOverview(UUID.fromString(user.username)))

    @GetMapping("/structures/{id}")
    fun structureAnalytics(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<StructureAnalyticsResponse> =
        ResponseEntity.ok(analyticsUseCase.getStructureAnalytics(UUID.fromString(user.username), id))

    @GetMapping("/structures/{id}/churn")
    fun churnAnalytics(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<ChurnAnalyticsResponse> =
        ResponseEntity.ok(analyticsUseCase.getChurnAnalytics(UUID.fromString(user.username), id))

    @GetMapping("/structures/{id}/utm")
    fun utmAnalytics(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "30") days: Int
    ): ResponseEntity<UtmAnalyticsResponse> =
        ResponseEntity.ok(analyticsUseCase.getUtmAnalytics(UUID.fromString(user.username), id, days))
}
