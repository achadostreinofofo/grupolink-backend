package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.model.WhatsappAccount
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WhatsappAccountRepository : JpaRepository<WhatsappAccount, UUID> {
    fun findAllByOwnerAndActive(owner: User, active: Boolean): List<WhatsappAccount>
    fun findByPhoneNumberId(phoneNumberId: String): WhatsappAccount?
    fun countByOwnerAndActive(owner: User, active: Boolean): Long
}
