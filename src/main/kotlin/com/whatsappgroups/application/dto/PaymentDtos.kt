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
    val periodEndDate: String?,
    val trialEndDate: String?,     // só para plano FREE (7 dias a partir de createdAt)
    val trialDaysLeft: Int?        // dias restantes do trial; 0 = expirado
)

data class DirectSubscribeRequest(
    val plan: String,
    val cardToken: String,
    val payerEmail: String,
    val identificationType: String = "CPF",
    val identificationNumber: String,
)

data class DirectSubscribeResponse(
    val subscriptionId: String,
    val status: String,
)
