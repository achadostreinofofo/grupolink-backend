package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "short_links", indexes = [
    Index(name = "idx_short_links_code",  columnList = "code", unique = true),
    Index(name = "idx_short_links_owner", columnList = "owner_id")
])
class ShortLink(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    // Slug único: ex "promos-ml", "ofertas-nike", ou gerado automaticamente
    @Column(unique = true, nullable = false)
    var code: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var targetUrl: String,

    @Column
    var title: String? = null,

    @Column(nullable = false)
    var clicks: Long = 0,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column
    var expiresAt: LocalDateTime? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
