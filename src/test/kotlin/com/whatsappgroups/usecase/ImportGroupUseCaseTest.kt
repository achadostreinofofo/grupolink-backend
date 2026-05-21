@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.AddGroupRequest
import com.whatsappgroups.application.dto.ImportGroupRequest
import com.whatsappgroups.application.usecase.structure.StructureUseCase
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

    private val useCase = StructureUseCase(
        structureRepo, groupRepo, userRepo, sessionRepo, whatsappClient, "http://localhost:8080"
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

    // ──────────────────────────── importGroup ────────────────────────────

    @Test
    fun `importGroup - sucesso salva grupo como ACTIVE com jid e inviteLink`() {
        val jid = "111@g.us"
        val link = "https://chat.whatsapp.com/ABC"
        val groupInfo = WebServiceGroupDetail(
            groupId = jid, name = "Treino Fofo #1", participants = 10,
            inviteLink = link, profilePicUrl = "https://pic.url/g.jpg"
        )
        // doReturn evita NPE em tipos Kotlin não-anuláveis durante setup do stub
        doReturn(groupInfo).whenever(whatsappClient).getGroupInfo("sess-1", jid)
        whenever(groupRepo.save(any())).thenReturn(savedGroup("Treino Fofo #1", jid, link))

        val result = useCase.importGroup(ownerId, structureId, ImportGroupRequest(whatsappGroupId = jid))

        assert(result.status == "ACTIVE")
        assert(result.whatsappGroupId == jid)
        verify(groupRepo).save(argThat { status == GroupStatus.ACTIVE && whatsappGroupId == jid })
        verify(whatsappClient, never()).getGroupInviteLink(any(), any())
    }

    @Test
    fun `importGroup - usa inviteLink da request quando fornecido`() {
        val jid = "222@g.us"
        val customLink = "https://chat.whatsapp.com/CUSTOM"
        val groupInfo = WebServiceGroupDetail(groupId = jid, name = "Treino Fofo #1", participants = 5, inviteLink = null)
        doReturn(groupInfo).whenever(whatsappClient).getGroupInfo("sess-1", jid)
        whenever(groupRepo.save(any())).thenReturn(savedGroup("Treino Fofo #1", jid, customLink))

        useCase.importGroup(ownerId, structureId, ImportGroupRequest(whatsappGroupId = jid, inviteLink = customLink))

        verify(groupRepo).save(argThat { inviteLink == customLink })
        verify(whatsappClient, never()).getGroupInviteLink(any(), any())
    }

    @Test
    fun `importGroup - busca inviteLink automaticamente quando groupInfo e request sao null`() {
        val jid = "333@g.us"
        val autoLink = "https://chat.whatsapp.com/AUTO"
        val groupInfo = WebServiceGroupDetail(groupId = jid, name = "Treino Fofo #1", participants = 3, inviteLink = null)
        doReturn(groupInfo).whenever(whatsappClient).getGroupInfo("sess-1", jid)
        doReturn(autoLink).whenever(whatsappClient).getGroupInviteLink("sess-1", jid)
        whenever(groupRepo.save(any())).thenReturn(savedGroup("Treino Fofo #1", jid, autoLink))

        useCase.importGroup(ownerId, structureId, ImportGroupRequest(whatsappGroupId = jid))

        verify(whatsappClient).getGroupInviteLink("sess-1", jid)
    }

    @Test
    fun `importGroup - lanca erro se estrutura ja tem grupo ativo`() {
        whenever(groupRepo.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE))
            .thenReturn(listOf(savedGroup("G #1")))

        assertThrows<IllegalStateException> {
            useCase.importGroup(ownerId, structureId, ImportGroupRequest(whatsappGroupId = "444@g.us"))
        }
    }

    @Test
    fun `importGroup - lanca erro se nao ha sessao autenticada`() {
        whenever(sessionRepo.findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED))
            .thenReturn(Optional.empty())

        assertThrows<IllegalStateException> {
            useCase.importGroup(ownerId, structureId, ImportGroupRequest(whatsappGroupId = "555@g.us"))
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
        whenever(groupRepo.save(any())).thenReturn(
            savedGroup("Treino Fofo #1", status = GroupStatus.CREATING)
        )

        val result = useCase.addGroup(
            ownerId, structureId,
            AddGroupRequest(name = "Treino Fofo", profilePicUrl = "https://pic.url/g.jpg")
        )

        assert(result.name == "Treino Fofo #1")
    }
}
