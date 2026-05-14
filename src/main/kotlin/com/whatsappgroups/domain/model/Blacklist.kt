package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "blacklist", indexes = [
    Index(name = "idx_blacklist_phone", columnList = "phoneNumber"),
    Index(name = "idx_blacklist_owner", columnList = "owner_id")
])
class Blacklist(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(nullable = false)
    val phoneNumber: String,

    @Column
    val reason: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
