package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class Plan { FREE, SMART, DIAMOND, BLACK }
enum class UserStatus { PENDING_VERIFICATION, ACTIVE }

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(nullable = false)
    var passwordHash: String,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var plan: Plan = Plan.FREE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(unique = true)
    var cpf: String? = null,

    @Column
    var phone: String? = null,

    @Column
    var whatsappPhone: String? = null,

    @Column
    var whatsappBusinessAccountId: String? = null,

    @Column(nullable = false)
    var whatsappIntegrated: Boolean = false,

    @Column(name = "email_verification_token", length = 128)
    var emailVerificationToken: String? = null,

    @Column(name = "email_verified_at")
    var emailVerifiedAt: LocalDateTime? = null,

    @Column(name = "password_reset_token", length = 128)
    var passwordResetToken: String? = null,

    @Column(name = "password_reset_expires_at")
    var passwordResetExpiresAt: LocalDateTime? = null,

    @OneToMany(mappedBy = "owner", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val structures: MutableList<Structure> = mutableListOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
