package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.Blacklist
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface BlacklistRepository : JpaRepository<Blacklist, UUID> {
    fun findAllByOwner(owner: User): List<Blacklist>
    fun existsByOwnerAndPhoneNumber(owner: User, phoneNumber: String): Boolean

    @Query("SELECT b.phoneNumber FROM Blacklist b WHERE b.owner = :owner")
    fun findPhoneNumbersByOwner(owner: User): Set<String>
}
