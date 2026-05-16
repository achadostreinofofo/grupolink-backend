package com.whatsappgroups.infrastructure.scheduler

import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class TrialCleanupScheduler(
    private val userRepository: UserRepository,
    private val structureRepository: StructureRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // Roda a cada hora
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    fun cleanupExpiredTrials() {
        val now = LocalDateTime.now()
        val expiredUsers = userRepository.findExpiredTrialUsers(Plan.SMART, now)

        if (expiredUsers.isEmpty()) return

        log.info("Trial expirado para ${expiredUsers.size} usuário(s). Removendo estruturas...")

        for (user in expiredUsers) {
            structureRepository.deleteAllByOwner(user)
            user.plan = Plan.FREE
            user.trialEndsAt = null
            user.updatedAt = now
            log.info("Trial expirado: ${user.email} → plano FREE, estruturas removidas")
        }
    }
}
