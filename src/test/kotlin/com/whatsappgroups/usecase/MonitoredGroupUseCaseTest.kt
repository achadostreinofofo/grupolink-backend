@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.*
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.application.usecase.whatsapp.BroadcastUseCase
import com.whatsappgroups.application.usecase.whatsapp.MonitoredGroupUseCase
import com.whatsappgroups.domain.model.*
import com.whatsappgroups.domain.repository.*
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonitoredGroupUseCaseTest {

    @Mock private lateinit var monitoredGroupRepository: MonitoredGroupRepository
    @Mock private lateinit var webSessionRepository: WhatsappWebSessionRepository
    @Mock private lateinit var structureRepository: StructureRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var webServiceClient: WhatsappWebServiceClient
    @Mock private lateinit var broadcastUseCase: BroadcastUseCase
    @Mock private lateinit var mlAccountRepository: MercadoLivreAccountRepository
    @Mock private lateinit var mlAccountUseCase: MercadoLivreAccountUseCase

    private lateinit var useCase: MonitoredGroupUseCase
    private val userId = UUID.randomUUID()
    private val structureId = UUID.randomUUID()
    private val sessionId = "session-abc"
    private val monitoredId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        useCase = MonitoredGroupUseCase(
            monitoredGroupRepository, webSessionRepository, structureRepository,
            userRepository, webServiceClient, broadcastUseCase, mlAccountRepository, mlAccountUseCase)
    }

    @Test
    fun `create saves monitored group successfully`() {
        val owner = user(); val session = session(); val structure = structure()
        val saved = MonitoredGroup(id = monitoredId, owner = owner, webSession = session, structure = structure, whatsappGroupId = "group123@g.us")
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(owner))
        whenever(webSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session))
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.of(structure))
        whenever(monitoredGroupRepository.findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(any(), any())).thenReturn(Optional.empty())
        whenever(monitoredGroupRepository.save(any()) as MonitoredGroup?).thenReturn(saved)

        val result = useCase.create(userId, CreateMonitoredGroupRequest(sessionId = sessionId, whatsappGroupId = "group123@g.us", structureId = structureId.toString()))

        assertThat(result.whatsappGroupId).isEqualTo("group123@g.us")
    }

    @Test
    fun `create throws when session not authenticated`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user()))
        whenever(webSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session(WebSessionStatus.WAITING_SCAN)))

        assertThatThrownBy { useCase.create(userId, CreateMonitoredGroupRequest(sessionId = sessionId, whatsappGroupId = "g@g.us", structureId = structureId.toString())) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `create throws when group already monitored`() {
        val owner = user(); val session = session(); val structure = structure()
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(owner))
        whenever(webSessionRepository.findBySessionId(sessionId)).thenReturn(Optional.of(session))
        whenever(structureRepository.findById(structureId)).thenReturn(Optional.of(structure))
        whenever(monitoredGroupRepository.findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(any(), any()))
            .thenReturn(Optional.of(monitoredGroup(owner, session, structure)))

        assertThatThrownBy { useCase.create(userId, CreateMonitoredGroupRequest(sessionId = sessionId, whatsappGroupId = "g@g.us", structureId = structureId.toString())) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `list returns monitored groups`() {
        val owner = user()
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(monitoredGroupRepository.findAllByOwner(owner)).thenReturn(listOf(monitoredGroup(owner, session(), structure())))
        assertThat(useCase.list(userId)).hasSize(1)
    }

    @Test
    fun `delete removes monitored group`() {
        val owner = user()
        val mg = monitoredGroup(owner, session(), structure())
        whenever(userRepository.getReferenceById(userId)).thenReturn(owner)
        whenever(monitoredGroupRepository.findByIdAndOwner(monitoredId, owner)).thenReturn(Optional.of(mg))

        useCase.delete(userId, monitoredId)
        verify(monitoredGroupRepository).delete(mg)
    }

    @Test
    fun `processIncomingMessage does nothing when group not monitored`() {
        whenever(monitoredGroupRepository.findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(any(), any())).thenReturn(Optional.empty())
        useCase.processIncomingMessage(payload())
        verify(mlAccountRepository, never()).findByOwner(any())
    }

    @Test
    fun `processIncomingMessage discards when no ML account`() {
        val owner = user()
        whenever(monitoredGroupRepository.findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(any(), any()))
            .thenReturn(Optional.of(monitoredGroup(owner, session(), structure())))
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.empty())

        useCase.processIncomingMessage(payload())
        verify(broadcastUseCase, never()).broadcast(any(), any(), any())
    }

    @Test
    fun `processIncomingMessage broadcasts text message`() {
        val owner = user()
        val structure = structureWithGroups(1)
        whenever(monitoredGroupRepository.findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(any(), any()))
            .thenReturn(Optional.of(monitoredGroup(owner, session(), structure)))
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(mlAccount(owner)))
        whenever(mlAccountUseCase.resolveAndReplaceLinks(any(), any())).thenAnswer { inv -> inv.getArgument(0) }
        whenever(broadcastUseCase.broadcast(any(), any(), any())).thenReturn(mock())

        useCase.processIncomingMessage(payload(text = "Check https://meli.la/abc"))

        verify(broadcastUseCase).broadcast(eq(userId), eq(structureId), argThat<BroadcastMessageRequest> { content.contains("Check") })
    }

    @Test
    fun `processIncomingMessage sends image directly per group`() {
        val owner = user()
        val structure = structureWithGroups(2)
        whenever(monitoredGroupRepository.findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(any(), any()))
            .thenReturn(Optional.of(monitoredGroup(owner, session(), structure)))
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(mlAccount(owner)))
        whenever(mlAccountUseCase.resolveAndReplaceLinks(any(), any())).thenAnswer { inv -> inv.getArgument(0) }

        useCase.processIncomingMessage(payload(imageBase64 = "data=="))

        verify(webServiceClient, times(2)).sendImageBase64Message(any(), any(), any(), any())
        verify(broadcastUseCase, never()).broadcast(any(), any(), any())
    }

    @Test
    fun `processIncomingMessage prepends messagePrefix`() {
        val owner = user()
        val structure = structureWithGroups(1)
        whenever(monitoredGroupRepository.findFirstByWebSession_SessionIdAndWhatsappGroupIdAndActiveTrue(any(), any()))
            .thenReturn(Optional.of(monitoredGroup(owner, session(), structure, prefix = "🔥")))
        whenever(mlAccountRepository.findByOwner(owner)).thenReturn(Optional.of(mlAccount(owner)))
        whenever(mlAccountUseCase.resolveAndReplaceLinks(any(), any())).thenReturn("https://x.com")
        whenever(broadcastUseCase.broadcast(any(), any(), any())).thenReturn(mock())

        useCase.processIncomingMessage(payload())
        verify(broadcastUseCase).broadcast(any(), any(), argThat<BroadcastMessageRequest> { content.startsWith("🔥") })
    }

    private fun user() = User(id = userId, email = "u@t.com", passwordHash = "h", name = "U")
    private fun session(status: WebSessionStatus = WebSessionStatus.AUTHENTICATED) = WhatsappWebSession(id = UUID.randomUUID(), owner = user(), sessionId = sessionId, status = status)
    private fun structure() = Structure(id = structureId, owner = user(), name = "S", slug = "s")
    private fun structureWithGroups(count: Int): Structure {
        val s = structure()
        repeat(count) { i -> s.groups.add(WhatsappGroup(id = UUID.randomUUID(), structure = s, name = "G$i", status = GroupStatus.ACTIVE, whatsappGroupId = "g$i@g.us")) }
        return s
    }
    private fun monitoredGroup(owner: User, session: WhatsappWebSession, structure: Structure, prefix: String? = null) =
        MonitoredGroup(id = monitoredId, owner = owner, webSession = session, structure = structure, whatsappGroupId = "monitored@g.us", messagePrefix = prefix)
    private fun mlAccount(owner: User) = MercadoLivreAccount(id = UUID.randomUUID(), owner = owner, mlUserId = "ml-999", mlNickname = "Seller", accessToken = "token")
    private fun payload(text: String = "https://meli.la/test", imageBase64: String? = null) =
        IncomingWhatsappMessageRequest(sessionId = sessionId, groupId = "monitored@g.us", senderJid = "s@s.net", messageId = "m1", text = text, imageBase64 = imageBase64, imageMimeType = null, timestamp = 0)
}
