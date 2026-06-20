@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.AddGroupRequest
import com.whatsappgroups.application.dto.ImportGroupsRequest
import com.whatsappgroups.application.usecase.structure.StructureUseCase
import com.whatsappgroups.application.usecase.whatsapp.ConnectedAccountsService
import com.whatsappgroups.domain.model.*
import com.whatsappgroups.domain.repository.*
import com.whatsappgroups.infrastructure.whatsapp.WebServiceGroupDetail
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class ImportGroupUseCaseTest {

    private val structureRepo  = mock<StructureRepository>()
    private val groupRepo      = mock<WhatsappGroupRepository>()
    private val userRepo       = mock<UserRepository>()
    private val sessionRepo    = mock<WhatsappWebSessionRepository>()
    private val whatsappClient = mock<WhatsappWebServiceClient>()
    private val connectedAccounts = mock<ConnectedAccountsService>()

    private val useCase = StructureUseCase(
        structureRepo, groupRepo, userRepo, sessionRepo, whatsappClient, connectedAccounts, "http://localhost:8080"
    )

    private val ownerId     = UUID.randomUUID()
    private val structureId = UUID.randomUUID()
    private val owner       = User(id = ownerId, name = "Owner", email = "o@test.com", cpf = "00000000000", passwordHash = "x")
    private val structure   = Structure(id = structureId, owner = owner, name = "Treino Fofo", slug = "treino-fofo")
    private val session     = WhatsappWebSession(owner = owner, sessionId = "sess-1", status = WebSessionStatus.AUTHENTICATED)

    @BeforeEach
    fun setup() {
        whenever(structureRepo.findById(structureId)).thenReturn(Optional.of(structure))
        whenever(sessionRepo.findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED))
            .thenReturn(Optional.of(session))
        whenever(groupRepo.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE))
            .thenReturn(emptyList())
        whenever(groupRepo.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.CREATING))
            .thenReturn(emptyList())
    }

    private fun savedGroup(
        name: String,
        jid: String? = null,
        inviteLink: String? = null,
        status: GroupStatus = GroupStatus.ACTIVE
    ) = WhatsappGroup(
        id = UUID.randomUUID(),
        structure = structure,
        name = name,
        whatsappGroupId = jid,
        inviteLink = inviteLink,
        maxMembers = 256,
        sortOrder = 0,
        status = status
    )

    // `save()` é @NonNull Spring Data → cast para nullable para evitar NPE no setup do stub
    private fun stubSave(group: WhatsappGroup) {
        whenever(groupRepo.save(any()) as WhatsappGroup?).thenReturn(group)
    }

    // ──────────────────────────── importGroups ────────────────────────────

    // save() retorna o próprio argumento, para a resposta refletir os valores gravados
    private fun stubSaveEcho() {
        whenever(groupRepo.save(any()) as WhatsappGroup?).thenAnswer { it.getArgument<WhatsappGroup>(0) }
    }

    @Test
    fun `importGroups - importa multiplos como ACTIVE com memberCount e sortOrder incremental`() {
        whenever(whatsappClient.getGroupInfo("sess-1", "111@g.us")).thenReturn(
            WebServiceGroupDetail(groupId = "111@g.us", name = "Grupo A", participants = 10, inviteLink = "https://chat.whatsapp.com/A")
        )
        whenever(whatsappClient.getGroupInfo("sess-1", "222@g.us")).thenReturn(
            WebServiceGroupDetail(groupId = "222@g.us", name = "Grupo B", participants = 20, inviteLink = "https://chat.whatsapp.com/B")
        )
        stubSaveEcho()

        val result = useCase.importGroups(ownerId, structureId,
            ImportGroupsRequest(whatsappGroupIds = listOf("111@g.us", "222@g.us")))

        assert(result.imported.size == 2)
        assert(result.failed.isEmpty())
        verify(groupRepo).save(argThat { whatsappGroupId == "111@g.us" && memberCount == 10 && sortOrder == 0 && status == GroupStatus.ACTIVE })
        verify(groupRepo).save(argThat { whatsappGroupId == "222@g.us" && memberCount == 20 && sortOrder == 1 })
    }

    @Test
    fun `importGroups - maxMembers nunca fica abaixo do grupo mais cheio importado`() {
        whenever(whatsappClient.getGroupInfo("sess-1", "111@g.us")).thenReturn(
            WebServiceGroupDetail(groupId = "111@g.us", name = "A", participants = 300, inviteLink = "l1")
        )
        stubSaveEcho()

        // pede 256, mas o grupo já tem 300 → limite efetivo = 300
        useCase.importGroups(ownerId, structureId,
            ImportGroupsRequest(whatsappGroupIds = listOf("111@g.us"), maxMembersPerGroup = 256))

        assert(structure.maxMembersPerGroup == 300)
        verify(groupRepo).save(argThat { maxMembers == 300 })
    }

    @Test
    fun `importGroups - falha parcial reporta o que falhou e importa os demais`() {
        whenever(whatsappClient.getGroupInfo("sess-1", "ok@g.us")).thenReturn(
            WebServiceGroupDetail(groupId = "ok@g.us", name = "OK", participants = 5, inviteLink = "l")
        )
        whenever(whatsappClient.getGroupInfo("sess-1", "bad@g.us")).thenReturn(null)
        stubSaveEcho()

        val result = useCase.importGroups(ownerId, structureId,
            ImportGroupsRequest(whatsappGroupIds = listOf("ok@g.us", "bad@g.us")))

        assert(result.imported.size == 1)
        assert(result.failed.size == 1 && result.failed.first().whatsappGroupId == "bad@g.us")
    }

    @Test
    fun `importGroups - busca inviteLink automaticamente quando ausente`() {
        whenever(whatsappClient.getGroupInfo("sess-1", "333@g.us")).thenReturn(
            WebServiceGroupDetail(groupId = "333@g.us", name = "C", participants = 3, inviteLink = null)
        )
        whenever(whatsappClient.getGroupInviteLink("sess-1", "333@g.us")).thenReturn("https://chat.whatsapp.com/AUTO")
        stubSaveEcho()

        useCase.importGroups(ownerId, structureId, ImportGroupsRequest(whatsappGroupIds = listOf("333@g.us")))

        verify(whatsappClient).getGroupInviteLink("sess-1", "333@g.us")
    }

    @Test
    fun `importGroups - excede o limite de grupos do plano lanca erro`() {
        // owner FREE permite 5 grupos/estrutura — tentar 6 deve falhar
        val ids = (1..6).map { "$it@g.us" }
        assertThrows<IllegalArgumentException> {
            useCase.importGroups(ownerId, structureId, ImportGroupsRequest(whatsappGroupIds = ids))
        }
    }

    @Test
    fun `importGroups - lanca erro se nao ha sessao autenticada`() {
        whenever(sessionRepo.findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED))
            .thenReturn(Optional.empty())

        assertThrows<IllegalStateException> {
            useCase.importGroups(ownerId, structureId, ImportGroupsRequest(whatsappGroupIds = listOf("555@g.us")))
        }
    }

    // ──────────────────────────── addGroup — regra primeiro grupo ────────────────────────────

    @Test
    fun `addGroup - lanca erro quando estrutura ja tem grupo ativo`() {
        whenever(groupRepo.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE))
            .thenReturn(listOf(savedGroup("G #1")))

        assertThrows<IllegalStateException> {
            useCase.addGroup(ownerId, structureId, AddGroupRequest(name = "Treino Fofo", profilePicUrl = "https://pic.url"))
        }
    }

    @Test
    fun `addGroup - permite criar quando nao ha grupos ativos`() {
        stubSave(savedGroup("Treino Fofo #1", status = GroupStatus.CREATING))

        val result = useCase.addGroup(
            ownerId, structureId,
            AddGroupRequest(name = "Treino Fofo", profilePicUrl = "https://pic.url/g.jpg")
        )

        assert(result.name == "Treino Fofo #1")
    }
}
