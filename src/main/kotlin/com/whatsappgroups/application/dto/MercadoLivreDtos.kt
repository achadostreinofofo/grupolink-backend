package com.whatsappgroups.application.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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

@JsonIgnoreProperties(ignoreUnknown = true)
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

// Catalog product (/products/{id}). Used as a fallback when /items/{id} returns 403 for
// catalog products. Title is `name`; price comes from the buy box winner.
@JsonIgnoreProperties(ignoreUnknown = true)
data class MlProductDetails(
    val id: String?,
    val name: String?,
    @JsonProperty("buy_box_winner") val buyBoxWinner: MlBuyBoxWinner?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MlBuyBoxWinner(
    val price: Double?,
    @JsonProperty("original_price") val originalPrice: Double?
)

data class GenerateMessageRequest(
    @field:NotBlank(message = "productUrl é obrigatório")
    val productUrl: String
)

data class GenerateMessageResponse(
    val content: String
)
