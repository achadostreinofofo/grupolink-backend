package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "ml_accounts", uniqueConstraints = [UniqueConstraint(columnNames = ["owner_id"])])
class MercadoLivreAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(name = "ml_user_id", nullable = false, length = 64)
    val mlUserId: String,

    @Column(name = "ml_nickname", length = 128)
    val mlNickname: String? = null,

    @Column(nullable = false, columnDefinition = "TEXT")
    var accessToken: String,

    @Column(columnDefinition = "TEXT")
    var refreshToken: String? = null,

    @Column(name = "token_expires_at")
    var tokenExpiresAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
