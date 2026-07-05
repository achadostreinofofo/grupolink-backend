package com.whatsappgroups.application.dto

import java.math.BigDecimal

data class AnalyticsOverviewResponse(
    val usersByPlan: Map<String, Int>,           // {"FREE": 45, "SMART": 12, "DIAMOND": 3}
    val totalUsers: Int,
    val activeSubscriptions: Int,
    val mrr: BigDecimal,                          // monthly recurring revenue
    val newUsersLast7Days: Int,
    val newUsersLast30Days: Int,
    val churnRate: Double,                        // % de usuários inativos > 30 dias
    val arpu: BigDecimal,                         // average revenue per user
    val roi: Double                               // (LTV - CAC) / CAC (simplificado)
)

data class AnalyticsUserDetail(
    val plan: String,
    val count: Int,
    val activeSubscriptions: Int,
    val totalRevenue: BigDecimal
)
