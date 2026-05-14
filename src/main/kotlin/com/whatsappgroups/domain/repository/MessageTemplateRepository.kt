package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.MessageTemplate
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MessageTemplateRepository : JpaRepository<MessageTemplate, UUID> {
    fun findAllByOwnerOrderByCreatedAtDesc(owner: User): List<MessageTemplate>
}
