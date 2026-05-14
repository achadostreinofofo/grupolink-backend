package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "whatsapp_accounts")
class WhatsappAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(nullable = false)
    var phoneNumberId: String,

    // Token de acesso permanente da Meta (armazenado criptografado)
    @Column(nullable = false, columnDefinition = "TEXT")
    var accessToken: String,

    @Column
    var displayName: String? = null,

    @Column
    var businessAccountId: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
