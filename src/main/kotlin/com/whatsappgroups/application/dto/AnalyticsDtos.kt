package com.whatsappgroups.application.dto

data class DailyClick(val date: String, val clicks: Long)

data class GroupAnalytics(
    val groupId: String,
    val groupName: String,
    val memberCount: Int,
    val maxMembers: Int,
    val capacityPercentage: Double,
    val clicksTotal: Long,
    val status: String
)

data class StructureAnalyticsResponse(
    val structureId: String,
    val structureName: String,
    val smartLink: String,
    val totalClicks: Long,
    val totalMembers: Int,
    val clicksLast7Days: Long,
    val clicksByDay: List<DailyClick>,
    val groups: List<GroupAnalytics>
)

data class OverviewAnalyticsResponse(
    val totalStructures: Int,
    val totalGroups: Int,
    val totalMembers: Int,
    val totalClicks: Long,
    val clicksLast7Days: Long,
    val clicksByDay: List<DailyClick>
)

// Churn
data class DailyExit(val date: String, val exits: Long)

data class GroupChurn(val groupId: String, val groupName: String, val exits: Long)

data class ChurnAnalyticsResponse(
    val structureId: String,
    val totalExits: Long,
    val totalJoins: Long,
    val churnRate: Double,           // exits / joins nos últimos 30d
    val exitsByDay: List<DailyExit>,
    val exitsByGroup: List<GroupChurn>
)

// UTM
data class UtmEntry(val label: String, val clicks: Long, val percentage: Double)

data class UtmAnalyticsResponse(
    val structureId: String,
    val period: String,
    val bySources: List<UtmEntry>,
    val byMedium: List<UtmEntry>,
    val byCampaign: List<UtmEntry>
)
