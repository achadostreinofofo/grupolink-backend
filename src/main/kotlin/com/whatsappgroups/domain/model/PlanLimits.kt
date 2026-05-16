package com.whatsappgroups.domain.model

data class PlanLimits(
    val maxStructures: Int,
    val maxWhatsappAccounts: Int,
    val maxSavedMessages: Int,
    val cookieMaxDays: Int,          // 0 = fixed 90d (SMART), 30 = configurável (Diamond/Black)
    val hasAnalytics: Boolean,
    val hasUrlShortener: Boolean,
)

fun Plan.limits(): PlanLimits = when (this) {
    Plan.FREE    -> PlanLimits(0, 0, 0, 0, false, false)
    Plan.SMART   -> PlanLimits(1, 1, 40, 0, false, false)
    Plan.DIAMOND -> PlanLimits(5, 2, Int.MAX_VALUE, 30, true, false)
    Plan.BLACK   -> PlanLimits(10, Int.MAX_VALUE, Int.MAX_VALUE, 30, true, true)
}
