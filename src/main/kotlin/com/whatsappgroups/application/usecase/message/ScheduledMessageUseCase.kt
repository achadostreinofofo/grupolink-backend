package com.whatsappgroups.application.usecase.message

import com.whatsappgroups.application.dto.CreateScheduledMessageRequest
import com.whatsappgroups.application.dto.ScheduleSlotResponse
import com.whatsappgroups.application.dto.ScheduledMessageResponse
import com.whatsappgroups.application.dto.UpdateScheduledMessageRequest
import com.whatsappgroups.application.usecase.whatsapp.BroadcastUseCase
import com.whatsappgroups.application.dto.BroadcastMessageRequest
import com.whatsappgroups.domain.model.MessageStatus
import com.whatsappgroups.domain.model.ScheduledMessage
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.repository.ScheduledMessageRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.config.OwnerAccount
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ScheduledMessageUseCase(
    private val messageRepository: ScheduledMessageRepository,
    private val userRepository: UserRepository,
    private val structureRepository: StructureRepository,
    private val broadcastUseCase: BroadcastUseCase
) {

    @Transactional
    fun create(userId: UUID, request: CreateScheduledMessageRequest): ScheduledMessageResponse {
        val owner = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("Usuário não encontrado") }

        val maxAllowed = if (OwnerAccount.isOwner(owner.email)) Int.MAX_VALUE
                         else owner.plan.maxScheduledMessagesPerMonth
        val now = LocalDateTime.now()
        val monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        val monthEnd = monthStart.plusMonths(1)
        val countThisMonth = messageRepository.countByOwnerAndCreatedAtBetween(owner, monthStart, monthEnd)
        if (countThisMonth >= maxAllowed) {
            throw IllegalArgumentException(
                "Seu plano ${owner.plan.name} permite no máximo $maxAllowed agendamento(s) por mês. " +
                "Faça upgrade para aumentar seu limite."
            )
        }

        val structure = request.structureId?.let {
            structureRepository.findById(UUID.fromString(it))
                .orElseThrow { NoSuchElementException("Estrutura não encontrada: $it") }
                .also { s -> if (s.owner.id != userId) throw IllegalAccessException("Acesso negado") }
        }

        // Agendamento (scheduledAt != null) deve cair num slot válido da estrutura.
        // Envio instantâneo (sendNow) não passa por aqui e não é afetado.
        if (request.scheduledAt != null && structure != null) {
            validateScheduleSlot(structure, request.scheduledAt)
        }

        val status = if (request.scheduledAt != null) MessageStatus.PENDING else MessageStatus.DRAFT

        val message = messageRepository.save(
            ScheduledMessage(
                owner       = owner,
                structure   = structure,
                title       = request.title,
                content     = request.content,
                mediaUrl    = request.mediaUrl,
                status      = status,
                scheduledAt = request.scheduledAt
            )
        )

        return message.toResponse()
    }

    @Transactional
    fun update(userId: UUID, messageId: UUID, request: UpdateScheduledMessageRequest): ScheduledMessageResponse {
        val message = findOwned(userId, messageId)

        if (message.status == MessageStatus.CANCELLED) {
            throw IllegalStateException("Não é possível editar uma mensagem cancelada")
        }

        if (request.scheduledAt != null) {
            message.structure?.let { validateScheduleSlot(it, request.scheduledAt, excludeMessageId = message.id) }
        }

        message.title       = request.title
        message.content     = request.content
        message.mediaUrl    = request.mediaUrl
        message.scheduledAt = request.scheduledAt
        message.status      = if (request.scheduledAt != null) MessageStatus.PENDING else MessageStatus.DRAFT
        message.updatedAt   = LocalDateTime.now()

        return message.toResponse()
    }

    @Transactional
    fun sendNow(userId: UUID, messageId: UUID, groupIds: List<String>? = null): ScheduledMessageResponse {
        val message = findOwned(userId, messageId)

        val structure = message.structure
            ?: throw IllegalStateException("Mensagem não está associada a uma estrutura")

        if (message.status == MessageStatus.CANCELLED) throw IllegalStateException("Mensagem foi cancelada")

        // Triggers async broadcast via RabbitMQ
        val messageType = if (message.mediaUrl != null) "IMAGE" else "TEXT"
        broadcastUseCase.broadcast(
            userId      = userId,
            structureId = structure.id!!,
            request     = BroadcastMessageRequest(
                messageType = messageType,
                content     = message.content,
                mediaUrl    = message.mediaUrl,
                groupIds    = groupIds
            )
        )

        message.status      = MessageStatus.SENT
        message.executedAt  = LocalDateTime.now()
        message.updatedAt   = LocalDateTime.now()

        return message.toResponse()
    }

    fun listByUser(userId: UUID): List<ScheduledMessageResponse> {
        val owner = userRepository.getReferenceById(userId)
        return messageRepository.findAllByOwner(owner)
            .sortedByDescending { it.updatedAt }
            .map { it.toResponse() }
    }

    fun listByStructure(userId: UUID, structureId: UUID): List<ScheduledMessageResponse> {
        val owner = userRepository.getReferenceById(userId)
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        return messageRepository.findAllByOwnerAndStructureId(owner, structureId)
            .sortedByDescending { it.updatedAt }
            .map { it.toResponse() }
    }

    @Transactional
    fun cancel(userId: UUID, messageId: UUID) {
        val message = findOwned(userId, messageId)
        if (message.status != MessageStatus.PENDING && message.status != MessageStatus.DRAFT) {
            throw IllegalStateException("Só é possível cancelar mensagens com status DRAFT ou PENDING")
        }
        message.status    = MessageStatus.CANCELLED
        message.updatedAt = LocalDateTime.now()
    }

    @Transactional
    fun delete(userId: UUID, messageId: UUID) {
        val message = findOwned(userId, messageId)
        messageRepository.delete(message)
    }

    // Grade de horários de um dia para a estrutura: AVAILABLE (livre) · TAKEN (já agendado) · PAST.
    fun getAvailableSlots(userId: UUID, structureId: UUID, date: LocalDate): List<ScheduleSlotResponse> {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val interval = structure.scheduleIntervalMinutes.toLong()
        val now = LocalDateTime.now()

        val taken = messageRepository.findAllByStructureIdAndStatusAndScheduledAtBetween(
            structureId, MessageStatus.PENDING, date.atStartOfDay(), date.plusDays(1).atStartOfDay()
        ).mapNotNull { it.scheduledAt?.truncatedTo(ChronoUnit.MINUTES) }.toSet()

        val slots = mutableListOf<ScheduleSlotResponse>()
        var t = structure.scheduleWindowStart
        while (t.isBefore(structure.scheduleWindowEnd)) {
            val dt = LocalDateTime.of(date, t)
            val status = when {
                !dt.isAfter(now)    -> "PAST"
                taken.contains(dt)  -> "TAKEN"
                else                -> "AVAILABLE"
            }
            slots.add(ScheduleSlotResponse(
                time      = t.format(HHMM),
                datetime  = dt.toString(),
                available = status == "AVAILABLE",
                status    = status
            ))
            val next = t.plusMinutes(interval)
            if (!next.isAfter(t)) break   // protege contra virada de meia-noite
            t = next
        }
        return slots
    }

    // Garante que o horário escolhido para agendamento cabe na grade da estrutura.
    private fun validateScheduleSlot(structure: Structure, scheduledAt: LocalDateTime, excludeMessageId: UUID? = null) {
        if (!scheduledAt.isAfter(LocalDateTime.now()))
            throw IllegalArgumentException("O horário escolhido já passou. Selecione um horário futuro.")

        val time  = scheduledAt.toLocalTime()
        val start = structure.scheduleWindowStart
        val end   = structure.scheduleWindowEnd
        if (time.isBefore(start) || !time.isBefore(end))
            throw IllegalArgumentException("O horário deve estar entre ${start.format(HHMM)} e ${end.format(HHMM)}.")

        val minutesFromStart = Duration.between(start, time).toMinutes()
        if (minutesFromStart % structure.scheduleIntervalMinutes != 0L)
            throw IllegalArgumentException("O horário deve respeitar o intervalo de ${structure.scheduleIntervalMinutes} minutos definido na estrutura.")

        val slot = scheduledAt.truncatedTo(ChronoUnit.MINUTES)
        val occupied = messageRepository.findAllByStructureIdAndStatusAndScheduledAtBetween(
            structure.id!!, MessageStatus.PENDING, slot, slot.plusMinutes(1)
        ).any { it.id != excludeMessageId && it.scheduledAt?.truncatedTo(ChronoUnit.MINUTES) == slot }
        if (occupied)
            throw IllegalArgumentException("Já existe uma mensagem agendada para este horário nesta estrutura.")
    }

    private fun findOwned(userId: UUID, messageId: UUID): ScheduledMessage {
        val message = messageRepository.findById(messageId)
            .orElseThrow { NoSuchElementException("Mensagem não encontrada") }
        if (message.owner.id != userId) throw IllegalAccessException("Acesso negado")
        return message
    }

    private fun ScheduledMessage.toResponse() = ScheduledMessageResponse(
        id            = id.toString(),
        title         = title,
        content       = content,
        mediaUrl      = mediaUrl,
        status        = status.name,
        scheduledAt   = scheduledAt?.toString(),
        executedAt    = executedAt?.toString(),
        errorMessage  = errorMessage,
        structureId   = structure?.id?.toString(),
        structureName = structure?.name
    )

    private companion object {
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
