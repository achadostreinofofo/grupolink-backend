package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.dto.IncomingWhatsappMessageRequest
import com.whatsappgroups.application.usecase.whatsapp.MonitoredGroupUseCase
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * Webhook interno chamado pelo whatsapp-service quando uma mensagem com link
 * https://meli.la/... é capturada em um grupo monitorado.
 *
 * Autenticação via header `x-api-secret`.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp/internal")
class MonitoredGroupWebhookController(
    @Value("\${app.whatsapp-service.secret}") private val expectedSecret: String,
    private val monitoredGroupUseCase: MonitoredGroupUseCase
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/message")
    fun receiveMessage(
        request: HttpServletRequest,
        @Valid @RequestBody payload: IncomingWhatsappMessageRequest
    ): ResponseEntity<Void> {
        if (!isAuthorized(request)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        monitoredGroupUseCase.processIncomingMessage(payload)
        return ResponseEntity.accepted().build()
    }

    private fun isAuthorized(request: HttpServletRequest): Boolean {
        val secret = request.getHeader("x-api-secret")
        if (secret != expectedSecret) {
            log.warn("Requisição interna sem secret válido de ${request.remoteAddr}")
            return false
        }
        return true
    }
}
