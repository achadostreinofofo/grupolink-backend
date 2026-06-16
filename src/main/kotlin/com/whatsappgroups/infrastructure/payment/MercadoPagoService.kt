package com.whatsappgroups.infrastructure.payment

import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest
import com.mercadopago.client.preapproval.PreapprovalClient
import com.mercadopago.client.preapproval.PreapprovalCreateRequest
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest
import com.mercadopago.exceptions.MPApiException
import com.mercadopago.resources.preapproval.Preapproval
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.util.UUID

@Service
class MercadoPagoService(
    @Value("\${app.frontend-url}") private val frontendUrl: String,
    @Value("\${app.mercadopago.access-token}") private val accessToken: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val subClient = PreapprovalClient()
    private val restClient = RestClient.create()

    val planPrices = mapOf(
        "SMART"   to BigDecimal("98.00"),
        "DIAMOND" to BigDecimal("179.00"),
        "BLACK"   to BigDecimal("299.00")
    )

    fun createSubscription(planName: String, userEmail: String): Preapproval {
        val price = planPrices[planName.uppercase()]
            ?: throw IllegalArgumentException("Plano desconhecido: $planName")

        return try {
            subClient.create(buildRequest(planName, price, userEmail))
        } catch (e: MPApiException) {
            // MP rejects payer_email when it belongs to a non-Brazilian MP account.
            // Retry with a neutral email so the user can still complete checkout normally.
            if (e.statusCode == 400 && e.apiResponse?.content?.contains("different countries") == true) {
                log.warn("payer_email $userEmail rejected by MP (country mismatch) — retrying with neutral email")
                subClient.create(buildRequest(planName, price, "checkout+${UUID.randomUUID()}@redirectgrupo.com.br"))
            } else throw e
        }
    }

    private fun buildRequest(planName: String, price: BigDecimal, payerEmail: String): PreapprovalCreateRequest =
        PreapprovalCreateRequest.builder()
            .reason("GrupoLink - Plano $planName")
            .payerEmail(payerEmail)
            .backUrl("$frontendUrl/billing/success?plan=${planName.lowercase()}")
            .status("pending")
            .autoRecurring(
                PreApprovalAutoRecurringCreateRequest.builder()
                    .frequency(1)
                    .frequencyType("months")
                    .transactionAmount(price)
                    .currencyId("BRL")
                    .build()
            )
            .build()

    @Suppress("UNCHECKED_CAST")
    fun createSubscriptionWithToken(planName: String, payerEmail: String, cardTokenId: String): String {
        val price = planPrices[planName.uppercase()]
            ?: throw IllegalArgumentException("Plano desconhecido: $planName")

        val body = mapOf(
            "reason"          to "GrupoLink - Plano $planName",
            "payer_email"     to payerEmail,
            "card_token_id"   to cardTokenId,
            "status"          to "authorized",
            "auto_recurring"  to mapOf(
                "frequency"          to 1,
                "frequency_type"     to "months",
                "transaction_amount" to price,
                "currency_id"        to "BRL"
            )
        )

        val response = restClient.post()
            .uri("https://api.mercadopago.com/preapproval")
            .header("Authorization", "Bearer $accessToken")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .onStatus({ it.is4xxClientError || it.is5xxServerError }) { _, res ->
                val errorBody = res.body.bufferedReader().readText()
                log.error("Erro MP tokenized checkout ${res.statusCode.value()}: $errorBody")
                throw RuntimeException("Erro ao processar pagamento: $errorBody")
            }
            .body(Map::class.java) as? Map<String, Any?>

        return response?.get("id") as? String
            ?: throw RuntimeException("Mercado Pago não retornou ID de assinatura")
    }

    fun getSubscription(mpSubscriptionId: String): Preapproval? =
        runCatching { subClient.get(mpSubscriptionId) }
            .getOrElse {
                log.warn("Erro ao buscar assinatura MP $mpSubscriptionId: ${it.message}")
                null
            }

    fun cancelSubscription(mpSubscriptionId: String): Boolean =
        runCatching {
            val updateReq = PreapprovalUpdateRequest.builder()
                .status("cancelled")
                .build()
            subClient.update(mpSubscriptionId, updateReq)
            true
        }.getOrElse {
            log.error("Erro ao cancelar assinatura MP $mpSubscriptionId: ${it.message}")
            false
        }
}
