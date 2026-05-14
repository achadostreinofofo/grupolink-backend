package com.whatsappgroups.usecase

import com.whatsappgroups.application.usecase.redirect.ProcessRedirectUseCase
import com.whatsappgroups.application.usecase.redirect.RedirectContext
import com.whatsappgroups.domain.model.*
import com.whatsappgroups.domain.repository.*
import com.whatsappgroups.infrastructure.messaging.GroupEventPublisher
import com.whatsappgroups.infrastructure.redis.RedisSessionService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ProcessRedirectUseCaseTest {

    @Mock private lateinit var structureRepository: StructureRepository
    @Mock private lateinit var groupRepository: WhatsappGroupRepository
    @Mock private lateinit var redirectLogRepository: RedirectLogRepository
    @Mock private lateinit var memberRepository: GroupMemberRepository
    @Mock private lateinit var redisSessionService: RedisSessionService
    @Mock private lateinit var groupEventPublisher: GroupEventPublisher

    @InjectMocks
    private lateinit var useCase: ProcessRedirectUseCase

    private lateinit var owner: User
    private lateinit var structure: Structure
    private lateinit var group: WhatsappGroup

    @BeforeEach
    fun setUp() {
        owner = User(
            id           = UUID.randomUUID(),
            email        = "test@example.com",
            passwordHash = "hash",
            name         = "Test User"
        )
        structure = Structure(
            id            = UUID.randomUUID(),
            owner         = owner,
            name          = "Test Structure",
            slug          = "test-slug",
            fillThreshold = 0.80
        )
        group = WhatsappGroup(
            id          = UUID.randomUUID(),
            structure   = structure,
            name        = "Group 1",
            inviteLink  = "https://chat.whatsapp.com/test123",
            maxMembers  = 256,
            memberCount = 0,
            status      = GroupStatus.ACTIVE
        )
    }

    @Test
    fun `new visitor is redirected to first available group`() {
        given(structureRepository.findBySlug("test-slug")).willReturn(structure)
        given(redisSessionService.getGroupForCookie("new-cookie", structure.id!!)).willReturn(null)
        given(groupRepository.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE))
            .willReturn(listOf(group))
        given(redisSessionService.getAndIncrementRoundRobinIndex(structure.id!!)).willReturn(0L)
        given(memberRepository.findByGroupAndCookieId(group, "new-cookie")).willReturn(null)

        val result = useCase.execute(ctx("new-cookie"))

        assertThat(result.inviteLink).isEqualTo("https://chat.whatsapp.com/test123")
        assertThat(result.isReturnVisitor).isFalse()
        assertThat(result.groupId).isEqualTo(group.id)

        verify(redirectLogRepository).save(any())
        verify(memberRepository).save(any())
        verify(groupEventPublisher).publishCreationCheck(structure.id!!)
    }

    @Test
    fun `return visitor is redirected to same group without saving new log`() {
        given(structureRepository.findBySlug("test-slug")).willReturn(structure)
        given(redisSessionService.getGroupForCookie("returning-cookie", structure.id!!))
            .willReturn(group.id!!)
        given(groupRepository.findById(group.id!!)).willReturn(Optional.of(group))

        val result = useCase.execute(ctx("returning-cookie"))

        assertThat(result.isReturnVisitor).isTrue()
        assertThat(result.groupId).isEqualTo(group.id)

        verify(redirectLogRepository, never()).save(any())
        verify(groupEventPublisher, never()).publishCreationCheck(any())
    }

    @Test
    fun `throws when structure not found`() {
        given(structureRepository.findBySlug("unknown")).willReturn(null)

        assertThatThrownBy { useCase.execute(ctx("any-cookie", slug = "unknown")) }
            .isInstanceOf(NoSuchElementException::class.java)
            .hasMessageContaining("unknown")
    }

    @Test
    fun `throws when no active groups available`() {
        given(structureRepository.findBySlug("test-slug")).willReturn(structure)
        given(redisSessionService.getGroupForCookie(any(), any())).willReturn(null)
        given(groupRepository.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE))
            .willReturn(emptyList())

        assertThatThrownBy { useCase.execute(ctx("any-cookie")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Nenhum grupo disponível")
    }

    @Test
    fun `round-robin distributes between groups above threshold`() {
        val group2 = WhatsappGroup(
            id = UUID.randomUUID(), structure = structure, name = "Group 2",
            inviteLink = "https://chat.whatsapp.com/group2",
            maxMembers = 10, memberCount = 9, // 90% — acima do threshold
            status = GroupStatus.ACTIVE
        )
        val group3 = WhatsappGroup(
            id = UUID.randomUUID(), structure = structure, name = "Group 3",
            inviteLink = "https://chat.whatsapp.com/group3",
            maxMembers = 10, memberCount = 2, // 20% — abaixo do threshold
            status = GroupStatus.ACTIVE
        )

        given(structureRepository.findBySlug("test-slug")).willReturn(structure)
        given(redisSessionService.getGroupForCookie(any(), any())).willReturn(null)
        given(groupRepository.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE))
            .willReturn(listOf(group2, group3))
        given(redisSessionService.getAndIncrementRoundRobinIndex(any())).willReturn(0L)
        given(memberRepository.findByGroupAndCookieId(any(), any())).willReturn(null)

        val result = useCase.execute(ctx("new-cookie"))

        // Com group2 acima do threshold e group3 livre: os dois entram no pool (distribuição 2 em 2)
        assertThat(result.inviteLink).isIn(
            "https://chat.whatsapp.com/group2",
            "https://chat.whatsapp.com/group3"
        )
    }

    @Test
    fun `stale cookie binding is cleared when group is inactive`() {
        val inactiveGroup = WhatsappGroup(
            id = group.id, structure = structure, name = "Group 1",
            inviteLink = null, // sem invite link = inativo
            maxMembers = 256, memberCount = 0, status = GroupStatus.INACTIVE
        )

        given(structureRepository.findBySlug("test-slug")).willReturn(structure)
        given(redisSessionService.getGroupForCookie("stale-cookie", structure.id!!)).willReturn(group.id!!)
        given(groupRepository.findById(group.id!!)).willReturn(Optional.of(inactiveGroup))

        // Após limpar o binding, deve buscar um novo grupo — sem grupos disponíveis → exception
        given(groupRepository.findAllByStructureAndStatusOrderBySortOrderAsc(structure, GroupStatus.ACTIVE))
            .willReturn(emptyList())

        assertThatThrownBy { useCase.execute(ctx("stale-cookie")) }
            .isInstanceOf(IllegalStateException::class.java)

        verify(redisSessionService).deleteCookieBinding("stale-cookie", structure.id!!)
    }

    private fun ctx(cookieId: String, slug: String = "test-slug") = RedirectContext(
        structureSlug = slug,
        cookieId      = cookieId,
        ipAddress     = "192.168.1.1",
        userAgent     = "Mozilla/5.0 (test)"
    )
}
