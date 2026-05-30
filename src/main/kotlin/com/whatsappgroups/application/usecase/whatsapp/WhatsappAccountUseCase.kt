package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.application.dto.ConnectWhatsappRequest
import com.whatsappgroups.application.dto.WhatsappAccountResponse
import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.WhatsappAccount
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappAccountRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappCloudApiClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class WhatsappAccountUseCase(
    private val whatsappAccountRepository: WhatsappAccountRepository,
    private val userRepository: UserRepository,
    private val apiClient: WhatsappCloudApiClient
) {

    @Transactional
    fun connect(userId: UUID, request: ConnectWhatsappRequest): WhatsappAccountResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        // Limite de contas por plano
        val activeCount = whatsappAccountRepository.countByOwnerAndActive(user, true)
        val maxAccounts = if (com.whatsappgroups.infrastructure.config.OwnerAccount.isOwner(user.email)) Long.MAX_VALUE
        else when (user.plan) {
            Plan.FREE    -> 1L
            Plan.SMART   -> 1L
            Plan.DIAMOND -> 2L
            Plan.BLACK, Plan.MAXIMUS -> Long.MAX_VALUE
        }

        if (activeCount >= maxAccounts) {
            throw IllegalArgumentException(
                "Seu plano ${user.plan.name} permite no máximo $maxAccounts conta(s) WhatsApp. " +
                "Faça upgrade para adicionar mais."
            )
        }

        // Valida o token consultando a API da Meta
        val phoneInfo = apiClient.verifyPhoneNumber(request.phoneNumberId, request.accessToken)
            ?: throw IllegalArgumentException(
                "Phone Number ID ou Access Token inválido. Verifique no Meta Developer Console."
            )

        val account = whatsappAccountRepository.save(
            WhatsappAccount(
                owner         = user,
                phoneNumberId = request.phoneNumberId,
                accessToken   = request.accessToken,
                displayName   = phoneInfo.verifiedName,
            )
        )

        return account.toResponse(phoneInfo.displayPhone)
    }

    fun listAccounts(userId: UUID): List<WhatsappAccountResponse> {
        val user = userRepository.getReferenceById(userId)
        return whatsappAccountRepository.findAllByOwnerAndActive(user, true)
            .map { it.toResponse() }
    }

    @Transactional
    fun disconnect(userId: UUID, accountId: UUID) {
        val account = whatsappAccountRepository.findById(accountId)
            .orElseThrow { NoSuchElementException("Conta não encontrada") }

        if (account.owner.id != userId) throw IllegalAccessException("Acesso negado")

        account.active = false
        account.updatedAt = LocalDateTime.now()
    }

    private fun WhatsappAccount.toResponse(displayPhone: String? = null) = WhatsappAccountResponse(
        id          = id.toString(),
        phoneNumberId = phoneNumberId,
        displayName   = displayName,
        displayPhone  = displayPhone,
        active        = active
    )
}
