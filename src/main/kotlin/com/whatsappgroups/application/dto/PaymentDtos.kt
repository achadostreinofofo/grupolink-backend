package com.whatsappgroups.application.dto

data class CreateCheckoutRequest(
    val plan: String  // SMART | DIAMOND | BLACK
)

data class CheckoutResponse(
    val checkoutUrl: String,
    val subscriptionId: String,
    val plan: String,
    val amount: Double
)

data class SubscriptionStatusResponse(
    val subscriptionId: String?,
    val plan: String,
    val status: String,
    val payerEmail: String?,
    val periodEndDate: String?
)
