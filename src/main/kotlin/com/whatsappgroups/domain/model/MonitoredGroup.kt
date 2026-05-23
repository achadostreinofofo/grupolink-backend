package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "monitored_groups")
class MonitoredGroup(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "web_session_id", nullable = false)
    var webSession: WhatsappWebSession,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    var structure: Structure,

    @Column(name = "whatsapp_group_id", nullable = false)
    val whatsappGroupId: String,

    @Column(name = "whatsapp_group_name")
    var whatsappGroupName: String? = null,

    @Column(name = "message_prefix", columnDefinition = "TEXT")
    var messagePrefix: String? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
