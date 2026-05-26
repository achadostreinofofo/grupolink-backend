package com.whatsappgroups.infrastructure.payment

import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest
import com.mercadopago.client.preapproval.PreapprovalClient
import com.mercadopago.client.preapproval.PreapprovalCreateRequest
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest
import com.mercadopago.exceptions.MPApiException
import com.mercadopago.resources.preapproval.Preapproval
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class MercadoPagoService(
    @Value("\${app.frontend-url}") private val frontendUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val subClient = PreapprovalClient()

    val planPrices = mapOf(
        "SMART"   to BigDecimal("128.00"),
        "DIAMOND" to BigDecimal("290.00"),
        "BLACK"   to BigDecimal("453.00")
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
