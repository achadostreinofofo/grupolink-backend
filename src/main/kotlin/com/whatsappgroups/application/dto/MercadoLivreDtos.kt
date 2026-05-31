package com.whatsappgroups.application.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

data class MlStatusResponse(
    val connected: Boolean,
    val nickname: String?,
    val tokenValid: Boolean = false,
    val tokenExpired: Boolean = false,
    val affiliateConfigured: Boolean = false,
    val error: String? = null
)

data class MlOAuthStartResponse(
    val authorizationUrl: String
)

data class MlAffiliateParamsRequest(
    val mattWord: String,
    val mattTool: String
)

data class MlItemDetails(
    val id: String,
    val title: String,
    val permalink: String?,
    val thumbnail: String?,
    val price: Double?,
    @JsonProperty("original_price") val originalPrice: Double?,
    @JsonProperty("currency_id") val currencyId: String?,
    val condition: String?,
    @JsonProperty("available_quantity") val availableQuantity: Int?
)

data class GenerateMessageRequest(
    @field:NotBlank(message = "productUrl é obrigatório")
    val productUrl: String
)

data class GenerateMessageResponse(
    val content: String
)
