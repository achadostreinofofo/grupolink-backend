package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class Plan { FREE, SMART, DIAMOND, BLACK }

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

    @Column(unique = true)
    var cpf: String? = null,

    @Column
    var trialEndsAt: java.time.LocalDateTime? = null,

    @Column
    var whatsappPhone: String? = null,

    @Column
    var whatsappBusinessAccountId: String? = null,

    @Column(nullable = false)
    var whatsappIntegrated: Boolean = false,

    @OneToMany(mappedBy = "owner", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val structures: MutableList<Structure> = mutableListOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
