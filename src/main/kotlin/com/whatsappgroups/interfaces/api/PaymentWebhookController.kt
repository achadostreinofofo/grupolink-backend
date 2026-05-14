package com.whatsappgroups.interfaces.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.whatsappgroups.application.usecase.payment.PaymentWebhookUseCase
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/*
 * Webhook do Mercado Pago (IPN / Notifications)
 *
 * Configurar no painel MP ou no PreapprovalCreateRequest:
 *   notification_url: https://seu-dominio.com/api/webhooks/mercadopago
 *
 * O MP envia um POST quando o status de uma assinatura muda (authorized, paused, cancelled…)
 */
@RestController
@RequestMapping("/api/webhooks/mercadopago")
class PaymentWebhookController(private val paymentWebhookUseCase: PaymentWebhookUseCase) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping
    fun receive(
        @RequestBody payload: MercadoPagoWebhookPayload,
        @RequestParam(required = false) id: String?,
        @RequestParam(required = false) topic: String?
    ): ResponseEntity<Void> {

        // O MP pode enviar via query params (IPN) ou via body (Webhooks)
        val resourceId = id
            ?: payload.data?.id
            ?: payload.resource?.substringAfterLast("/")

        val eventType = topic ?: payload.type ?: payload.action

        log.info("Webhook MP recebido: type=$eventType, id=$resourceId")

        // Processar apenas eventos de assinatura (preapproval)
        if (eventType?.contains("preapproval", ignoreCase = true) == true && resourceId != null) {
            runCatching { paymentWebhookUseCase.processPreapprovalEvent(resourceId) }
                .onFailure { log.error("Erro ao processar webhook MP $resourceId: ${it.message}", it) }
        }

        // Sempre retornar 200 para o MP não reenviar desnecessariamente
        return ResponseEntity.ok().build()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MercadoPagoWebhookPayload(
    val id: Long? = null,
    val type: String? = null,
    val action: String? = null,
    val resource: String? = null,
    val data: MpWebhookData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MpWebhookData(val id: String? = null)
