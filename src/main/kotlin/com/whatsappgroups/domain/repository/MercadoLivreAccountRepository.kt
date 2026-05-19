package com.whatsappgroups.domain.repository

import com.whatsappgroups.domain.model.MercadoLivreAccount
import com.whatsappgroups.domain.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface MercadoLivreAccountRepository : JpaRepository<MercadoLivreAccount, UUID> {
    fun findByOwner(owner: User): Optional<MercadoLivreAccount>
}
