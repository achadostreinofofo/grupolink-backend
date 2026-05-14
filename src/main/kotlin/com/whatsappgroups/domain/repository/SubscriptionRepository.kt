package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.Subscription
import com.whatsappgroups.domain.model.SubscriptionStatus
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    fun findByOwnerAndStatusIn(owner: User, statuses: List<SubscriptionStatus>): List<Subscription>
    fun findByMercadoPagoId(mercadoPagoId: String): Subscription?
}
