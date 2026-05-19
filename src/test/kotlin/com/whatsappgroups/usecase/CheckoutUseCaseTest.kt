@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.mercadopago.resources.preapproval.Preapproval
import com.whatsappgroups.application.usecase.payment.CheckoutUseCase
import com.whatsappgroups.domain.model.*
import com.whatsappgroups.domain.repository.SubscriptionRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.payment.MercadoPagoService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutUseCaseTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var subscriptionRepository: SubscriptionRepository
    @Mock private lateinit var mercadoPagoService: MercadoPagoService

    private lateinit var useCase: CheckoutUseCase
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        useCase = CheckoutUseCase(userRepository, subscriptionRepository, mercadoPagoService)
    }

    @Test
    fun `createCheckout creates subscription and returns checkout URL`() {
        val sub = subscription(plan = Plan.SMART)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(freeUser()))
        whenever(subscriptionRepository.save(any()) as Subscription?).thenReturn(sub)
        val preapproval = mock<Preapproval>()
        whenever(preapproval.id).thenReturn("mp-sub-id")
        whenever(preapproval.initPoint).thenReturn("https://mp.com/checkout")
        whenever(mercadoPagoService.createSubscription("SMART", "free@test.com")).thenReturn(preapproval)
        doReturn(mapOf("SMART" to BigDecimal("128.00"))).whenever(mercadoPagoService).planPrices

        val result = useCase.createCheckout(userId, "SMART")

        assertThat(result.checkoutUrl).isEqualTo("https://mp.com/checkout")
        assertThat(result.plan).isEqualTo("SMART")
        verify(subscriptionRepository).save(any())
    }

    @Test
    fun `createCheckout falls back to sandboxInitPoint when initPoint is null`() {
        val sub = subscription(plan = Plan.DIAMOND)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(freeUser()))
        whenever(subscriptionRepository.save(any()) as Subscription?).thenReturn(sub)
        val preapproval = mock<Preapproval>()
        whenever(preapproval.id).thenReturn("mp-id")
        whenever(preapproval.initPoint).thenReturn(null)
        whenever(preapproval.sandboxInitPoint).thenReturn("https://sandbox.mp.com")
        whenever(mercadoPagoService.createSubscription(any(), any())).thenReturn(preapproval)
        doReturn(mapOf("DIAMOND" to BigDecimal("290"))).whenever(mercadoPagoService).planPrices

        val result = useCase.createCheckout(userId, "DIAMOND")

        assertThat(result.checkoutUrl).isEqualTo("https://sandbox.mp.com")
    }

    @Test
    fun `createCheckout returns empty string when both init points are null`() {
        val sub = subscription(plan = Plan.BLACK)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(freeUser()))
        whenever(subscriptionRepository.save(any()) as Subscription?).thenReturn(sub)
        val preapproval = mock<Preapproval>()
        whenever(preapproval.id).thenReturn("mp-id")
        whenever(preapproval.initPoint).thenReturn(null)
        whenever(preapproval.sandboxInitPoint).thenReturn(null)
        whenever(mercadoPagoService.createSubscription(any(), any())).thenReturn(preapproval)
        doReturn(mapOf("BLACK" to BigDecimal("453"))).whenever(mercadoPagoService).planPrices

        val result = useCase.createCheckout(userId, "BLACK")

        assertThat(result.checkoutUrl).isEmpty()
    }

    @Test
    fun `createCheckout throws on FREE plan`() {
        assertThatThrownBy { useCase.createCheckout(userId, "FREE") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `createCheckout throws on invalid plan`() {
        assertThatThrownBy { useCase.createCheckout(userId, "INVALID") }
            .isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("Plano inválido")
    }

    @Test
    fun `createCheckout throws when user not found`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())
        assertThatThrownBy { useCase.createCheckout(userId, "SMART") }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `getActiveSubscription returns active subscription details`() {
        val u = User(id = userId, email = "t@t.com", passwordHash = "h", name = "T", plan = Plan.SMART)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(u))
        whenever(subscriptionRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(listOf(subscription(Plan.SMART, SubscriptionStatus.ACTIVE)))

        val result = useCase.getActiveSubscription(userId)

        assertThat(result.plan).isEqualTo("SMART")
        assertThat(result.status).isEqualTo("ACTIVE")
    }

    @Test
    fun `getActiveSubscription returns trial info for FREE user`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(freeUser()))
        whenever(subscriptionRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(emptyList())

        val result = useCase.getActiveSubscription(userId)

        assertThat(result.plan).isEqualTo("FREE")
        assertThat(result.trialDaysLeft).isNotNull()
    }

    @Test
    fun `getActiveSubscription returns zero trial days when expired`() {
        val u = User(id = userId, email = "t@t.com", passwordHash = "h", name = "T",
            plan = Plan.FREE, createdAt = LocalDateTime.now().minusDays(10))
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(u))
        whenever(subscriptionRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(emptyList())

        assertThat(useCase.getActiveSubscription(userId).trialDaysLeft).isEqualTo(0)
    }

    @Test
    fun `cancelSubscription cancels and reverts plan to FREE`() {
        val u = User(id = userId, email = "t@t.com", passwordHash = "h", name = "T", plan = Plan.SMART)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(u))
        whenever(subscriptionRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(listOf(subscription(Plan.SMART, SubscriptionStatus.ACTIVE, "mp-123")))

        useCase.cancelSubscription(userId)

        assertThat(u.plan).isEqualTo(Plan.FREE)
        verify(mercadoPagoService).cancelSubscription("mp-123")
    }

    @Test
    fun `cancelSubscription throws when no active subscription`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(freeUser()))
        whenever(subscriptionRepository.findByOwnerAndStatusIn(any(), any())).thenReturn(emptyList())

        assertThatThrownBy { useCase.cancelSubscription(userId) }.isInstanceOf(IllegalStateException::class.java)
    }

    private fun freeUser() = User(id = userId, email = "free@test.com", passwordHash = "hash",
        name = "Free User", plan = Plan.FREE, createdAt = LocalDateTime.now())

    private fun subscription(plan: Plan = Plan.SMART, status: SubscriptionStatus = SubscriptionStatus.PENDING, mpId: String? = "mp-sub-id") =
        Subscription(id = UUID.randomUUID(), owner = freeUser(), plan = plan, status = status, mercadoPagoId = mpId, amount = BigDecimal("128"))
}
