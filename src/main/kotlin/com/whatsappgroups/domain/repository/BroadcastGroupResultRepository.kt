package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.BroadcastGroupResult
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BroadcastGroupResultRepository : JpaRepository<BroadcastGroupResult, UUID> {
    fun findAllByBroadcastId(broadcastId: UUID): List<BroadcastGroupResult>
}
