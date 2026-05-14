package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class GroupStatus { ACTIVE, FULL, INACTIVE, CREATING }

@Entity
@Table(name = "whatsapp_groups")
class WhatsappGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    val structure: Structure,

    @Column(nullable = false)
    var name: String,

    @Column
    var whatsappGroupId: String? = null,

    @Column
    var inviteLink: String? = null,

    @Column(nullable = false)
    var maxMembers: Int = 256,

    @Column(nullable = false)
    var memberCount: Int = 0,

    @Column(nullable = false)
    var clickCount: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: GroupStatus = GroupStatus.ACTIVE,

    @Column(nullable = false)
    var sortOrder: Int = 0,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    val capacityPercentage: Double
        get() = if (maxMembers > 0) memberCount.toDouble() / maxMembers.toDouble() else 0.0

    val isFull: Boolean
        get() = memberCount >= maxMembers
}
