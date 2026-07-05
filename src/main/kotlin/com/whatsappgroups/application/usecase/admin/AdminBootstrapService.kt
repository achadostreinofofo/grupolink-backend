package com.whatsappgroups.application.usecase.admin

import com.whatsappgroups.domain.model.AdminUser
import com.whatsappgroups.domain.repository.AdminUserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AdminBootstrapService(
    private val adminUserRepository: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${app.admin.email:}") private val adminEmail: String,
    @Value("\${app.admin.password:}") private val adminPassword: String,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) return
        if (adminUserRepository.existsByEmail(adminEmail)) return

        adminUserRepository.save(
            AdminUser(
                email        = adminEmail,
                name         = "Admin",
                passwordHash = passwordEncoder.encode(adminPassword)
            )
        )
        log.info("Admin user created: $adminEmail")
    }
}
