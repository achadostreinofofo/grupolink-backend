package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

enum class SubscriptionStatus {
    PENDING, ACTIVE, PAUSED, CANCELLED, EXPIRED
}

@Entity
@Table(name = "subscriptions")
class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var plan: Plan,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.PENDING,

    // ID da assinatura no Mercado Pago (preapproval ID)
    @Column(unique = true)
    var mercadoPagoId: String? = null,

    // ID do pagador no MP
    @Column
    var payerEmail: String? = null,

    @Column(precision = 10, scale = 2)
    var amount: BigDecimal? = null,

    @Column
    var periodStartDate: LocalDateTime? = null,

    @Column
    var periodEndDate: LocalDateTime? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
