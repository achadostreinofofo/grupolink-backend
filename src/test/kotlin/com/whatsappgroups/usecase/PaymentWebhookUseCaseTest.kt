@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.mercadopago.resources.preapproval.Preapproval
import com.whatsappgroups.application.usecase.payment.PaymentWebhookUseCase
import com.whatsappgroups.domain.model.*
import com.whatsappgroups.domain.repository.SubscriptionRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.payment.MercadoPagoService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentWebhookUseCaseTest {

    @Mock private lateinit var subscriptionRepository: SubscriptionRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var mercadoPagoService: MercadoPagoService

    private lateinit var useCase: PaymentWebhookUseCase
    private val userId = UUID.randomUUID()
    private val mpId = "mp-sub-123"

    @BeforeEach
    fun setUp() {
        useCase = PaymentWebhookUseCase(subscriptionRepository, userRepository, mercadoPagoService)
    }

    @Test
    fun `processPreapprovalEvent does nothing when subscription not found`() {
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(null)

        useCase.processPreapprovalEvent(mpId)

        verify(mercadoPagoService, never()).getSubscription(any())
    }

    @Test
    fun `processPreapprovalEvent does nothing when MP returns null`() {
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(subscription())
        whenever(mercadoPagoService.getSubscription(mpId)).thenReturn(null)

        useCase.processPreapprovalEvent(mpId)

        verify(userRepository, never()).findById(any())
    }

    @Test
    fun `processPreapprovalEvent updates status to ACTIVE and upgrades user plan on authorized`() {
        val sub  = subscription(plan = Plan.SMART)
        val user = smartUser()
        // Pre-create mock BEFORE calling whenever(...).thenReturn(mpSub)
        // to avoid UnfinishedStubbingException (mpSubMock calls whenever() internally)
        val mpSub = mpSubMock("authorized", "payer@mp.com")
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(sub)
        whenever(mercadoPagoService.getSubscription(mpId)).thenReturn(mpSub)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

        useCase.processPreapprovalEvent(mpId)

        assertThat(sub.status).isEqualTo(SubscriptionStatus.ACTIVE)
        assertThat(sub.payerEmail).isEqualTo("payer@mp.com")
        assertThat(user.plan).isEqualTo(Plan.SMART)
    }

    @Test
    fun `processPreapprovalEvent sets plan to FREE on paused`() {
        val sub   = subscription(plan = Plan.SMART)
        val user  = smartUser()
        val mpSub = mpSubMock("paused")
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(sub)
        whenever(mercadoPagoService.getSubscription(mpId)).thenReturn(mpSub)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

        useCase.processPreapprovalEvent(mpId)

        assertThat(sub.status).isEqualTo(SubscriptionStatus.PAUSED)
        assertThat(user.plan).isEqualTo(Plan.FREE)
    }

    @Test
    fun `processPreapprovalEvent sets plan to FREE on cancelled`() {
        val sub   = subscription()
        val user  = smartUser()
        val mpSub = mpSubMock("cancelled")
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(sub)
        whenever(mercadoPagoService.getSubscription(mpId)).thenReturn(mpSub)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

        useCase.processPreapprovalEvent(mpId)

        assertThat(sub.status).isEqualTo(SubscriptionStatus.CANCELLED)
        assertThat(user.plan).isEqualTo(Plan.FREE)
    }

    @Test
    fun `processPreapprovalEvent sets status to PENDING on pending`() {
        val sub   = subscription()
        val user  = smartUser()
        val mpSub = mpSubMock("pending")
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(sub)
        whenever(mercadoPagoService.getSubscription(mpId)).thenReturn(mpSub)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))

        useCase.processPreapprovalEvent(mpId)

        assertThat(sub.status).isEqualTo(SubscriptionStatus.PENDING)
        assertThat(user.plan).isEqualTo(Plan.FREE)
    }

    @Test
    fun `processPreapprovalEvent does nothing on unknown status`() {
        val sub   = subscription()
        val mpSub = mock<Preapproval>()
        whenever(mpSub.status).thenReturn("unknown_status")
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(sub)
        whenever(mercadoPagoService.getSubscription(mpId)).thenReturn(mpSub)

        useCase.processPreapprovalEvent(mpId)

        verify(userRepository, never()).findById(any())
    }

    @Test
    fun `processPreapprovalEvent handles missing user gracefully`() {
        val sub   = subscription()
        val mpSub = mpSubMock("authorized")
        whenever(subscriptionRepository.findByMercadoPagoId(mpId)).thenReturn(sub)
        whenever(mercadoPagoService.getSubscription(mpId)).thenReturn(mpSub)
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        useCase.processPreapprovalEvent(mpId)

        assertThat(sub.status).isEqualTo(SubscriptionStatus.ACTIVE)
    }

    // ── helpers ─────────────────────────────────────────────────────

    private fun smartUser() = User(id = userId, email = "u@t.com", passwordHash = "h", name = "U", plan = Plan.SMART)

    private fun subscription(plan: Plan = Plan.SMART) = Subscription(
        id = UUID.randomUUID(), owner = smartUser(), plan = plan,
        status = SubscriptionStatus.PENDING, mercadoPagoId = mpId,
        amount = BigDecimal("128")
    )

    private fun mpSubMock(status: String, payerEmail: String? = null): Preapproval {
        val mp = mock<Preapproval>()
        whenever(mp.status).thenReturn(status)
        whenever(mp.payerEmail).thenReturn(payerEmail)
        return mp
    }
}
