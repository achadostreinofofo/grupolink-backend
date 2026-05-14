package com.whatsappgroups.infrastructure.payment

import com.mercadopago.client.preapproval.PreApprovalAutoRecurringCreateRequest
import com.mercadopago.client.preapproval.PreapprovalClient
import com.mercadopago.client.preapproval.PreapprovalCreateRequest
import com.mercadopago.client.preapproval.PreapprovalUpdateRequest
import com.mercadopago.resources.preapproval.Preapproval
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class MercadoPagoService(
    @Value("\${app.base-url}") private val baseUrl: String
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

        val req = PreapprovalCreateRequest.builder()
            .reason("GrupoLink - Plano $planName")
            .payerEmail(userEmail)
            .backUrl("$baseUrl/billing/success?plan=${planName.lowercase()}")
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

        return subClient.create(req)
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
