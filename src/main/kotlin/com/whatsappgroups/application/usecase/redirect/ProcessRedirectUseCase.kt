package com.whatsappgroups.application.usecase.redirect

import com.whatsappgroups.domain.model.GroupMember
import com.whatsappgroups.infrastructure.messaging.GroupEventPublisher
import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.model.MemberSource
import com.whatsappgroups.domain.model.RedirectLog
import com.whatsappgroups.domain.model.WhatsappGroup
import com.whatsappgroups.domain.repository.GroupMemberRepository
import com.whatsappgroups.domain.repository.RedirectLogRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import com.whatsappgroups.infrastructure.redis.RedisSessionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class RedirectContext(
    val structureSlug: String,
    val cookieId: String,
    val ipAddress: String?,
    val userAgent: String?,
    val utmSource: String? = null,
    val utmMedium: String? = null,
    val utmCampaign: String? = null,
    val utmContent: String? = null,
    val utmTerm: String? = null
)

data class RedirectResult(
    val inviteLink: String,
    val groupId: UUID,
    val isReturnVisitor: Boolean
)

@Service
class ProcessRedirectUseCase(
    private val structureRepository: StructureRepository,
    private val groupRepository: WhatsappGroupRepository,
    private val redirectLogRepository: RedirectLogRepository,
    private val memberRepository: GroupMemberRepository,
    private val redisSessionService: RedisSessionService,
    private val groupEventPublisher: GroupEventPublisher
) {

    @Transactional
    fun execute(ctx: RedirectContext): RedirectResult {
        val structure = structureRepository.findBySlug(ctx.structureSlug)
            ?: throw NoSuchElementException("Estrutura não encontrada: ${ctx.structureSlug}")

        // 1. Verifica no Redis se o cookie já foi direcionado para algum grupo
        redisSessionService.getGroupForCookie(ctx.cookieId, structure.id!!)?.let { groupId ->
            val group = groupRepository.findById(groupId).orElse(null)
            if (group != null && group.inviteLink != null && group.status == GroupStatus.ACTIVE) {
                groupRepository.incrementClick(groupId)
                return RedirectResult(group.inviteLink!!, groupId, isReturnVisitor = true)
            }
            // Grupo foi desativado/removido: limpar o binding e redirecionar para novo grupo
            redisSessionService.deleteCookieBinding(ctx.cookieId, structure.id)
        }

        // 2. Selecionar o grupo destino com algoritmo round-robin de 3 níveis
        val targetGroup = selectGroupRoundRobin(structure.id, structure.fillThreshold)
            ?: throw IllegalStateException("Nenhum grupo disponível na estrutura ${ctx.structureSlug}")

        // 3. Persistir log de redirecionamento
        redirectLogRepository.save(
            RedirectLog(
                structure = structure,
                group = targetGroup,
                cookieId = ctx.cookieId,
                ipAddress = ctx.ipAddress,
                userAgent = ctx.userAgent,
                utmSource = ctx.utmSource,
                utmMedium = ctx.utmMedium,
                utmCampaign = ctx.utmCampaign,
                utmContent = ctx.utmContent,
                utmTerm = ctx.utmTerm
            )
        )

        // 4. Incrementar contadores e salvar binding no Redis
        groupRepository.incrementMemberAndClick(targetGroup.id!!)
        redisSessionService.bindCookieToGroup(ctx.cookieId, structure.id, targetGroup.id)

        // 5. Rastrear membro via redirect (sem telefone, identificado pelo cookieId)
        if (memberRepository.findByGroupAndCookieId(targetGroup, ctx.cookieId) == null) {
            memberRepository.save(
                GroupMember(
                    group     = targetGroup,
                    cookieId  = ctx.cookieId,
                    source    = MemberSource.REDIRECT
                )
            )
        }

        // 6. Publicar evento de verificação de criação (via RabbitMQ, não bloqueia o redirect)
        groupEventPublisher.publishCreationCheck(structure.id)

        return RedirectResult(targetGroup.inviteLink!!, targetGroup.id, isReturnVisitor = false)
    }

    /*
     * Algoritmo Round-Robin de 3 Níveis:
     *
     * Nível 1: grupos com capacidade < threshold (ex: < 80%) recebem toda a demanda
     * Nível 2: quando um grupo atinge o threshold, os próximos 2 são ativados simultaneamente,
     *          e os 3 recebem tráfego em round-robin para distribuir organicamente
     * Nível 3: quando todos os ativos estão cheios, busca próximo grupo disponível
     *
     * O contador atômico no Redis garante distribuição sem race condition em alta concorrência.
     */
    private fun selectGroupRoundRobin(structureId: UUID, fillThreshold: Double): WhatsappGroup? {
        val activeGroups = groupRepository.findAllByStructureAndStatusOrderBySortOrderAsc(
            structureRepository.getReferenceById(structureId),
            GroupStatus.ACTIVE
        ).filter { it.inviteLink != null }

        if (activeGroups.isEmpty()) return null

        // Separar grupos abaixo do threshold (livres) dos que já atingiram o threshold
        val freeGroups = activeGroups.filter { it.capacityPercentage < fillThreshold }
        val thresholdGroups = activeGroups.filter { it.capacityPercentage >= fillThreshold && !it.isFull }

        val candidates: List<WhatsappGroup> = when {
            // Todos abaixo do threshold: usa apenas o primeiro (enchimento sequencial)
            freeGroups.size >= activeGroups.size && thresholdGroups.isEmpty() -> listOf(freeGroups.first())

            // Algum grupo atingiu threshold: distribui entre os 3 (threshold + próximos 2 livres)
            thresholdGroups.isNotEmpty() -> {
                val pool = (thresholdGroups + freeGroups.take(2)).distinctBy { it.id }
                pool.ifEmpty { freeGroups }
            }

            // Somente grupos cheios: busca qualquer grupo ativo não-cheio
            else -> activeGroups.filter { !it.isFull }
        }

        if (candidates.isEmpty()) return null

        // Round-robin atômico via Redis para distribuição uniforme em alta concorrência
        val index = redisSessionService.getAndIncrementRoundRobinIndex(structureId)
        return candidates[(index % candidates.size).toInt()]
    }
}
