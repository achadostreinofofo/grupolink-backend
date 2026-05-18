package com.whatsappgroups.application.usecase.payment

import com.whatsappgroups.application.dto.CheckoutResponse
import com.whatsappgroups.application.dto.SubscriptionStatusResponse
import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.Subscription
import com.whatsappgroups.domain.model.SubscriptionStatus
import java.time.temporal.ChronoUnit
import com.whatsappgroups.domain.repository.SubscriptionRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.payment.MercadoPagoService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class CheckoutUseCase(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val mercadoPagoService: MercadoPagoService,
) {

    @Transactional
    fun createCheckout(userId: UUID, planName: String): CheckoutResponse {
        val plan = runCatching { Plan.valueOf(planName.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Plano inválido: $planName. Use SMART, DIAMOND ou BLACK") }

        if (plan == Plan.FREE) throw IllegalArgumentException("Não é possível assinar o plano FREE")

        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val preapproval = mercadoPagoService.createSubscription(planName, user.email)

        // Persiste a assinatura como PENDING até o webhook confirmar
        val subscription = subscriptionRepository.save(
            Subscription(
                owner          = user,
                plan           = plan,
                status         = SubscriptionStatus.PENDING,
                mercadoPagoId  = preapproval.id,
                payerEmail     = user.email,
                amount         = mercadoPagoService.planPrices[planName.uppercase()]
            )
        )

        return CheckoutResponse(
            checkoutUrl    = preapproval.initPoint ?: preapproval.sandboxInitPoint ?: "",
            subscriptionId = subscription.id.toString(),
            plan           = planName.uppercase(),
            amount         = subscription.amount?.toDouble() ?: 0.0
        )
    }

    fun getActiveSubscription(userId: UUID): SubscriptionStatusResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }
        val active = subscriptionRepository
            .findByOwnerAndStatusIn(user, listOf(SubscriptionStatus.ACTIVE, SubscriptionStatus.PENDING))
            .firstOrNull()

        // Trial: 7 days from createdAt, only for FREE users
        val trialEnd  = if (user.plan == Plan.FREE) user.createdAt.plusDays(7) else null
        val daysLeft  = trialEnd?.let { maxOf(0L, ChronoUnit.DAYS.between(LocalDateTime.now(), it)).toInt() }

        return SubscriptionStatusResponse(
            subscriptionId = active?.id?.toString(),
            plan           = user.plan.name,
            status         = active?.status?.name ?: "NONE",
            payerEmail     = active?.payerEmail,
            periodEndDate  = active?.periodEndDate?.toString(),
            trialEndDate   = trialEnd?.toString(),
            trialDaysLeft  = daysLeft
        )
    }

    @Transactional
    fun cancelSubscription(userId: UUID) {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val active = subscriptionRepository
            .findByOwnerAndStatusIn(user, listOf(SubscriptionStatus.ACTIVE))
            .firstOrNull() ?: throw IllegalStateException("Nenhuma assinatura ativa encontrada")

        active.mercadoPagoId?.let { mercadoPagoService.cancelSubscription(it) }

        active.status = SubscriptionStatus.CANCELLED
        active.updatedAt = LocalDateTime.now()

        // Volta para FREE
        user.plan = Plan.FREE
        user.updatedAt = LocalDateTime.now()
    }
}
