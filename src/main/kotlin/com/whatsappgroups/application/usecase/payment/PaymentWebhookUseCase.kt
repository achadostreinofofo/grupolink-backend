package com.whatsappgroups.application.usecase.payment

import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.SubscriptionStatus
import com.whatsappgroups.domain.repository.SubscriptionRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.config.OwnerAccount
import com.whatsappgroups.infrastructure.payment.MercadoPagoService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

@Service
class PaymentWebhookUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val userRepository: UserRepository,
    private val mercadoPagoService: MercadoPagoService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun processPreapprovalEvent(mpSubscriptionId: String) {
        val subscription = subscriptionRepository.findByMercadoPagoId(mpSubscriptionId)
        if (subscription == null) {
            log.warn("Assinatura MP $mpSubscriptionId não encontrada no banco de dados")
            return
        }

        val mpSub = mercadoPagoService.getSubscription(mpSubscriptionId) ?: return

        val newStatus = when (mpSub.status?.lowercase()) {
            "authorized" -> SubscriptionStatus.ACTIVE
            "paused"     -> SubscriptionStatus.PAUSED
            "cancelled"  -> SubscriptionStatus.CANCELLED
            "pending"    -> SubscriptionStatus.PENDING
            else         -> {
                log.warn("Status desconhecido do MP: ${mpSub.status}")
                return
            }
        }

        log.info("Assinatura $mpSubscriptionId: ${subscription.status} -> $newStatus")

        subscription.status      = newStatus
        subscription.payerEmail  = mpSub.payerEmail ?: subscription.payerEmail
        subscription.updatedAt   = LocalDateTime.now()

        mpSub.nextPaymentDate?.let {
            subscription.periodEndDate = it.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
        }

        // Atualiza o plano do usuário conforme o status
        val user = userRepository.findById(subscription.owner.id!!)
            .orElse(null) ?: return

        if (OwnerAccount.isOwner(user.email)) return  // plano Maximus é imutável via webhook
        user.plan = if (newStatus == SubscriptionStatus.ACTIVE) subscription.plan else Plan.FREE
        user.updatedAt = LocalDateTime.now()

        log.info("Plano do usuário ${user.email} atualizado para ${user.plan}")
    }
}
