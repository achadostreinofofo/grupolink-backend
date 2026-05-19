package com.whatsappgroups.application.usecase.whatsapp

import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class SyncGroupMembersUseCase(
    private val groupRepository: WhatsappGroupRepository,
    private val sessionRepository: WhatsappWebSessionRepository,
    private val webServiceClient: WhatsappWebServiceClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    fun sync() {
        val groups = groupRepository.findAllByWhatsappGroupIdIsNotNull()
        if (groups.isEmpty()) return

        log.info("SyncGroupMembers: syncing ${groups.size} group(s)")

        val groupsByOwner = groups.groupBy { it.structure.owner.id }

        for ((_, ownerGroups) in groupsByOwner) {
            val owner = ownerGroups.first().structure.owner
            val session = sessionRepository
                .findFirstByOwnerAndStatus(owner, WebSessionStatus.AUTHENTICATED)
                .orElse(null) ?: continue

            for (group in ownerGroups) {
                val count = webServiceClient.getGroupParticipantCount(session.sessionId, group.whatsappGroupId!!)
                if (count == null) {
                    log.warn("SyncGroupMembers: could not fetch count for group ${group.id} (${group.name})")
                    continue
                }
                if (group.memberCount != count) {
                    log.info("SyncGroupMembers: group '${group.name}' ${group.memberCount} → $count")
                    group.memberCount = count
                    group.updatedAt = LocalDateTime.now()
                    groupRepository.save(group)
                }
            }
        }

        log.info("SyncGroupMembers: done")
    }
}
