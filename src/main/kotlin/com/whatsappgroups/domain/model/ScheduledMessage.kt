package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class MessageStatus { PENDING, SENT, FAILED, CANCELLED }

@Entity
@Table(name = "scheduled_messages")
class ScheduledMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id")
    val structure: Structure? = null,

    @Column(nullable = false)
    var title: String,

    @Column(columnDefinition = "TEXT", nullable = false)
    var content: String,

    @Column
    var mediaUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MessageStatus = MessageStatus.PENDING,

    @Column(nullable = false)
    var scheduledAt: LocalDateTime,

    @Column
    var executedAt: LocalDateTime? = null,

    @Column(columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
