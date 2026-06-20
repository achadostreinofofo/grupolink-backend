@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.CreateScheduledMessageRequest
import com.whatsappgroups.application.usecase.message.ScheduledMessageUseCase
import com.whatsappgroups.application.usecase.whatsapp.BroadcastUseCase
import com.whatsappgroups.domain.model.MessageStatus
import com.whatsappgroups.domain.model.ScheduledMessage
import com.whatsappgroups.domain.model.Structure
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.repository.ScheduledMessageRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

class ScheduleSlotsUseCaseTest {

    private val messageRepo     = mock<ScheduledMessageRepository>()
    private val userRepo        = mock<UserRepository>()
    private val structureRepo   = mock<StructureRepository>()
    private val broadcastUseCase = mock<BroadcastUseCase>()
    private val useCase = ScheduledMessageUseCase(messageRepo, userRepo, structureRepo, broadcastUseCase)

    private val userId      = UUID.randomUUID()
    private val structureId = UUID.randomUUID()
    private val owner       = User(id = userId, email = "o@test.com", passwordHash = "x", name = "Owner")
    // Janela curta para gerar só 3 slots: 08:00, 08:05, 08:10 (08:15 é o fim, exclusivo)
    private val structure   = Structure(
        id = structureId, owner = owner, name = "S", slug = "s",
        scheduleWindowStart = LocalTime.of(8, 0),
        scheduleWindowEnd = LocalTime.of(8, 15),
        scheduleIntervalMinutes = 5
    )

    @BeforeEach
    fun setup() {
        whenever(userRepo.findById(userId)).thenReturn(Optional.of(owner))
        whenever(structureRepo.findById(structureId)).thenReturn(Optional.of(structure))
        whenever(messageRepo.countByOwnerAndCreatedAtBetween(any(), any(), any())).thenReturn(0)
        whenever(messageRepo.save(any()) as ScheduledMessage?).thenAnswer { it.getArgument<ScheduledMessage>(0) }
        whenever(messageRepo.findAllByStructureIdAndStatusAndScheduledAtBetween(any(), any(), any(), any()))
            .thenReturn(emptyList())
    }

    private fun req(at: LocalDateTime) = CreateScheduledMessageRequest(
        title = "t", content = "c", structureId = structureId.toString(), scheduledAt = at
    )

    // ──────────────────────────── grade de slots ────────────────────────────

    @Test
    fun `getAvailableSlots marca livre, ocupado e respeita a grade`() {
        val day = LocalDate.now().plusDays(3)
        val taken = ScheduledMessage(
            owner = owner, structure = structure, title = "x", content = "x",
            status = MessageStatus.PENDING, scheduledAt = LocalDateTime.of(day, LocalTime.of(8, 5))
        )
        whenever(messageRepo.findAllByStructureIdAndStatusAndScheduledAtBetween(eq(structureId), eq(MessageStatus.PENDING), any(), any()))
            .thenReturn(listOf(taken))

        val slots = useCase.getAvailableSlots(userId, structureId, day)

        assertThat(slots.map { it.time }).containsExactly("08:00", "08:05", "08:10")
        assertThat(slots.first { it.time == "08:05" }.status).isEqualTo("TAKEN")
        assertThat(slots.first { it.time == "08:00" }.available).isTrue()
    }

    @Test
    fun `getAvailableSlots marca tudo como PAST para um dia passado`() {
        val slots = useCase.getAvailableSlots(userId, structureId, LocalDate.now().minusDays(1))
        assertThat(slots).isNotEmpty
        assertThat(slots).allMatch { it.status == "PAST" }
    }

    // ──────────────────────────── validação no agendamento ──────────────────

    @Test
    fun `create rejeita horário fora da janela`() {
        val dt = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(7, 0))
        assertThrows<IllegalArgumentException> { useCase.create(userId, req(dt)) }
    }

    @Test
    fun `create rejeita horário fora do passo do intervalo`() {
        val dt = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(8, 3)) // passo 5 min
        assertThrows<IllegalArgumentException> { useCase.create(userId, req(dt)) }
    }

    @Test
    fun `create rejeita horário no passado`() {
        val dt = LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(8, 5))
        assertThrows<IllegalArgumentException> { useCase.create(userId, req(dt)) }
    }

    @Test
    fun `create rejeita slot já ocupado`() {
        val dt = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(8, 5))
        val occupied = ScheduledMessage(
            id = UUID.randomUUID(), owner = owner, structure = structure, title = "x", content = "x",
            status = MessageStatus.PENDING, scheduledAt = dt
        )
        whenever(messageRepo.findAllByStructureIdAndStatusAndScheduledAtBetween(eq(structureId), eq(MessageStatus.PENDING), any(), any()))
            .thenReturn(listOf(occupied))

        assertThrows<IllegalArgumentException> { useCase.create(userId, req(dt)) }
    }

    @Test
    fun `create aceita um slot válido`() {
        val dt = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(8, 5))
        val res = useCase.create(userId, req(dt))
        assertThat(res.status).isEqualTo("PENDING")
    }
}
