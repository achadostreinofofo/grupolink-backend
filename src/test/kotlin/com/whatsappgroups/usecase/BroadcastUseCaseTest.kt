@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.BroadcastMessageRequest
import com.whatsappgroups.application.usecase.whatsapp.BroadcastUseCase
import com.whatsappgroups.domain.model.*
import com.whatsappgroups.domain.repository.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BroadcastUseCaseTest {

    @Mock private lateinit var broadcastRepository: MessageBroadcastRepository
    @Mock private lateinit var broadcastResultRepository: BroadcastGroupResultRepository
    @Mock private lateinit var structureRepository: StructureRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var rabbitTemplate: RabbitTemplate

    private lateinit var useCase: BroadcastUseCase
    private val userId = UUID.randomUUID()
    private val structureId = UUID.randomUUID()
    private val broadcastId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        useCase = BroadcastUseCase(broadcastRepository, broadcastResultRepository, structureRepository, userRepository, rabbitTemplate)
    }

    @Test
    fun `broadcast persists broadcast record and result rows for all active groups`() {
        val structure = structureWithGroups(2)
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.of(structure))
        whenever(broadcastRepository.save(any()) as MessageBroadcast?).thenReturn(broadcast(structure))
        whenever(broadcastResultRepository.save(any()) as BroadcastGroupResult?).thenReturn(BroadcastGroupResult(broadcastId = broadcastId, group = structure.groups.first()))

        runCatching { useCase.broadcast(userId, structureId, BroadcastMessageRequest(messageType = "TEXT", content = "Hello")) }

        verify(broadcastRepository).save(any())
        verify(broadcastResultRepository, times(2)).save(any())
    }

    @Test
    fun `broadcast sends only to specified group IDs`() {
        val structure = structureWithGroups(3)
        val targetId = structure.groups.first().id!!.toString()
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.of(structure))
        whenever(broadcastRepository.save(any()) as MessageBroadcast?).thenReturn(broadcast(structure, 1))
        whenever(broadcastResultRepository.save(any()) as BroadcastGroupResult?).thenReturn(BroadcastGroupResult(broadcastId = broadcastId, group = structure.groups.first()))

        runCatching { useCase.broadcast(userId, structureId, BroadcastMessageRequest(messageType = "TEXT", content = "X", groupIds = listOf(targetId))) }

        verify(broadcastResultRepository, times(1)).save(any())
    }

    @Test
    fun `broadcast uses TEXT type as default for invalid messageType`() {
        val structure = structureWithGroups(1)
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.of(structure))
        whenever(broadcastRepository.save(any()) as MessageBroadcast?).thenReturn(broadcast(structure))
        whenever(broadcastResultRepository.save(any()) as BroadcastGroupResult?).thenReturn(BroadcastGroupResult(broadcastId = broadcastId, group = structure.groups.first()))

        runCatching { useCase.broadcast(userId, structureId, BroadcastMessageRequest(messageType = "INVALID", content = "X")) }

        verify(broadcastRepository).save(check { assertThat(it.messageType).isEqualTo(BroadcastMessageType.TEXT) })
    }

    @Test
    fun `broadcast throws when structure not found`() {
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.empty())
        assertThatThrownBy { useCase.broadcast(userId, structureId, BroadcastMessageRequest(messageType = "TEXT", content = "x")) }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `broadcast throws when user does not own structure`() {
        val other = User(id = UUID.randomUUID(), email = "o@o.com", passwordHash = "h", name = "O")
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.of(structure(owner = other)))
        assertThatThrownBy { useCase.broadcast(userId, structureId, BroadcastMessageRequest(messageType = "TEXT", content = "x")) }
            .isInstanceOf(IllegalAccessException::class.java)
    }

    @Test
    fun `broadcast throws when no active groups`() {
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.of(structure()))
        assertThatThrownBy { useCase.broadcast(userId, structureId, BroadcastMessageRequest(messageType = "TEXT", content = "x")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `getStatus returns broadcast details for the owner`() {
        whenever(broadcastRepository.findById(broadcastId)).thenReturn(Optional.of(broadcast(structureWithGroups(1))))
        val result = useCase.getStatus(userId, broadcastId)
        assertThat(result.broadcastId).isEqualTo(broadcastId.toString())
        assertThat(result.status).isEqualTo("QUEUED")
    }

    @Test
    fun `getStatus throws when broadcast not found`() {
        whenever(broadcastRepository.findById(broadcastId)).thenReturn(Optional.empty())
        assertThatThrownBy { useCase.getStatus(userId, broadcastId) }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `getStatus throws for different user`() {
        whenever(broadcastRepository.findById(broadcastId)).thenReturn(Optional.of(broadcast(structureWithGroups(1))))
        assertThatThrownBy { useCase.getStatus(UUID.randomUUID(), broadcastId) }.isInstanceOf(IllegalAccessException::class.java)
    }

    @Test
    fun `listByUser returns all broadcasts`() {
        whenever(broadcastRepository.findAllByUserIdOrderByCreatedAtDesc(userId))
            .thenReturn(listOf(broadcast(structureWithGroups(1)), broadcast(structureWithGroups(1))))
        assertThat(useCase.listByUser(userId)).hasSize(2)
    }

    private fun owner() = User(id = userId, email = "u@t.com", passwordHash = "h", name = "U")
    private fun structure(owner: User = owner()) = Structure(id = structureId, owner = owner, name = "S", slug = "s")
    private fun structureWithGroups(count: Int): Structure {
        val s = structure()
        repeat(count) { s.groups.add(WhatsappGroup(id = UUID.randomUUID(), structure = s, name = "G$it", status = GroupStatus.ACTIVE, whatsappGroupId = "g$it@g.us")) }
        return s
    }
    private fun broadcast(structure: Structure, totalGroups: Int = 2) = MessageBroadcast(
        id = broadcastId, userId = userId, structure = structure,
        messageType = BroadcastMessageType.TEXT, content = "test",
        status = BroadcastStatus.QUEUED, totalGroups = totalGroups)
}
