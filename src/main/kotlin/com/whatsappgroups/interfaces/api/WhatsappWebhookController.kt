package com.whatsappgroups.interfaces.api

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.whatsappgroups.domain.model.GroupMember
import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.model.MemberSource
import com.whatsappgroups.domain.repository.GroupMemberRepository
import com.whatsappgroups.domain.repository.WhatsappAccountRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

/*
 * Webhook do WhatsApp Cloud API (Meta)
 *
 * Configuração no Meta Developer Console:
 *   URL do callback: https://seu-dominio.com/api/webhooks/whatsapp
 *   Token de verificação: valor de app.whatsapp.webhook-verify-token
 *   Campos: messages, message_deliveries, message_reads
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
class WhatsappWebhookController(
    @Value("\${app.whatsapp.webhook-verify-token}") private val verifyToken: String,
    private val whatsappAccountRepository: WhatsappAccountRepository,
    private val groupRepository: WhatsappGroupRepository,
    private val memberRepository: GroupMemberRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Verificação do webhook pela Meta (GET)
    @GetMapping
    fun verify(
        @RequestParam("hub.mode") mode: String?,
        @RequestParam("hub.verify_token") token: String?,
        @RequestParam("hub.challenge") challenge: String?
    ): ResponseEntity<String> {
        if (mode == "subscribe" && token == verifyToken) {
            log.info("Webhook WhatsApp verificado com sucesso")
            return ResponseEntity.ok(challenge)
        }
        return ResponseEntity.status(403).body("Token inválido")
    }

    // Recebe eventos da Meta (POST)
    @PostMapping
    @Transactional
    fun receiveEvent(@RequestBody payload: WhatsappWebhookPayload): ResponseEntity<Void> {
        payload.entry?.forEach { entry ->
            entry.changes?.forEach { change ->
                val phoneNumberId = change.value?.metadata?.get("phone_number_id") as? String

                change.value?.messages?.forEach { message ->
                    when (message.type) {
                        "system" -> processSystemEvent(phoneNumberId, message)
                        else     -> log.debug("Mensagem de ${message.from}: tipo=${message.type}")
                    }
                }
                change.value?.statuses?.forEach { status ->
                    log.debug("Status de entrega ${status.id}: ${status.status}")
                }
            }
        }
        return ResponseEntity.ok().build()
    }

    /*
     * Processa eventos de sistema do WhatsApp (entrada/saída de membros do grupo).
     * O WhatsApp Cloud API envia type=system com sub-tipos:
     *   - group_member_add / group_member_remove
     *   - user_changed_number
     */
    private fun processSystemEvent(phoneNumberId: String?, message: WhatsappIncomingMessage) {
        val systemType = message.system?.get("type") as? String ?: return
        val memberPhone = message.from ?: return

        log.info("Evento de sistema: tipo=$systemType, telefone=$memberPhone, phoneNumberId=$phoneNumberId")

        when (systemType) {
            "group_member_add", "user_changed_number" -> {
                // Localiza o grupo via phoneNumberId da conta conectada
                val group = resolveGroupByPhoneNumberId(phoneNumberId) ?: return

                val existing = memberRepository.findByGroupAndPhoneNumber(group, memberPhone)
                if (existing == null) {
                    memberRepository.save(
                        GroupMember(
                            group       = group,
                            phoneNumber = memberPhone,
                            name        = message.system["name"] as? String,
                            source      = MemberSource.WEBHOOK
                        )
                    )
                    // Incrementa contador se é um membro novo
                    if (group.memberCount < group.maxMembers) {
                        group.memberCount++
                        group.updatedAt = LocalDateTime.now()
                    }
                } else if (existing.leftAt != null) {
                    // Membro retornou
                    existing.leftAt = null
                }
            }

            "group_member_remove" -> {
                val group = resolveGroupByPhoneNumberId(phoneNumberId) ?: return
                val member = memberRepository.findByGroupAndPhoneNumber(group, memberPhone) ?: return
                member.leftAt = LocalDateTime.now()
                if (group.memberCount > 0) {
                    group.memberCount--
                    group.updatedAt = LocalDateTime.now()
                }
                log.info("Membro $memberPhone saiu do grupo ${group.name}")
            }
        }
    }

    private fun resolveGroupByPhoneNumberId(phoneNumberId: String?): com.whatsappgroups.domain.model.WhatsappGroup? {
        phoneNumberId ?: return null
        val account = whatsappAccountRepository.findByPhoneNumberId(phoneNumberId) ?: return null
        // Busca o primeiro grupo ACTIVE da estrutura associada ao phoneNumber
        // Em produção, o WhatsApp envia o groupId nos metadados — aqui usamos o primeiro ativo como simplificação
        return groupRepository.findAllByStructureAndStatusOrderBySortOrderAsc(
            account.owner.structures.firstOrNull() ?: return null,
            GroupStatus.ACTIVE
        ).firstOrNull()
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class WhatsappWebhookPayload(
    val `object`: String? = null,
    val entry: List<WhatsappEntry>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WhatsappEntry(
    val id: String? = null,
    val changes: List<WhatsappChange>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WhatsappChange(
    val value: WhatsappChangeValue? = null,
    val field: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WhatsappChangeValue(
    val messages: List<WhatsappIncomingMessage>? = null,
    val statuses: List<WhatsappMessageStatus>? = null,
    val metadata: Map<String, Any>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WhatsappIncomingMessage(
    val from: String? = null,
    val id: String? = null,
    val type: String? = null,
    val text: Map<String, Any>? = null,
    val system: Map<String, Any>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WhatsappMessageStatus(
    val id: String? = null,
    val status: String? = null,
    val recipientId: String? = null
)
