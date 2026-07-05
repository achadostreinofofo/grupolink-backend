package com.whatsappgroups.application.usecase.analytics

import com.whatsappgroups.application.dto.AnalyticsOverviewResponse
import com.whatsappgroups.domain.repository.AnalyticsRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class AnalyticsUseCase(
    private val analyticsRepository: AnalyticsRepository
) {
    fun getOverview(): AnalyticsOverviewResponse {
        val usersByPlanList = analyticsRepository.countUsersByPlan()
        val usersByPlan = usersByPlanList.associate { row ->
            (row["plan"] as String) to (row["count"] as Long).toInt()
        }

        val totalUsers = analyticsRepository.countTotalActiveUsers().toInt()
        val activeSubscriptions = analyticsRepository.countActiveSubscriptions().toInt()
        val mrr = analyticsRepository.calculateMRR()
        val newUsersLast7Days = analyticsRepository.countNewUsersLast7Days().toInt()
        val newUsersLast30Days = analyticsRepository.countNewUsersLast30Days().toInt()
        val inactiveUsers = analyticsRepository.countInactiveUsers30Days().toLong()
        val arpu = analyticsRepository.calculateARPU()

        val churnRate = if (totalUsers > 0) {
            (inactiveUsers.toDouble() / totalUsers.toDouble()) * 100.0
        } else {
            0.0
        }

        // ROI simplificado: assumir CAC = 50 (valor médio)
        // LTV = (ARPU * 12 meses) * retention (inverso do churn)
        val cacEstimate = BigDecimal(50)
        val ltv = if (arpu > BigDecimal.ZERO && churnRate < 100) {
            arpu.multiply(BigDecimal(12))
                .multiply(BigDecimal((100 - churnRate) / 100))
        } else {
            BigDecimal.ZERO
        }
        val roi = if (cacEstimate > BigDecimal.ZERO) {
            ((ltv - cacEstimate) / cacEstimate).toDouble() * 100
        } else {
            0.0
        }

        return AnalyticsOverviewResponse(
            usersByPlan = usersByPlan,
            totalUsers = totalUsers,
            activeSubscriptions = activeSubscriptions,
            mrr = mrr,
            newUsersLast7Days = newUsersLast7Days,
            newUsersLast30Days = newUsersLast30Days,
            churnRate = churnRate,
            arpu = arpu.setScale(2, RoundingMode.HALF_UP),
            roi = roi
        )
    }
}
