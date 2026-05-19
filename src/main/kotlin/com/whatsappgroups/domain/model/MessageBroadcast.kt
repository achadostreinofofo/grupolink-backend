package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class BroadcastStatus {
    QUEUED, IN_PROGRESS, COMPLETED, FAILED, SCHEDULED
}

enum class BroadcastMessageType {
    TEXT, IMAGE
}

enum class BroadcastGroupSendStatus {
    PENDING, SUCCESS, FAILED
}

@Entity
@Table(name = "message_broadcasts")
class MessageBroadcast(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    val structure: Structure,

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    val messageType: BroadcastMessageType = BroadcastMessageType.TEXT,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "media_url")
    val mediaUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BroadcastStatus = BroadcastStatus.QUEUED,

    @Column(name = "total_groups", nullable = false)
    var totalGroups: Int = 0,

    @Column(name = "groups_processed", nullable = false)
    var groupsProcessed: Int = 0,

    @Column(name = "groups_successful", nullable = false)
    var groupsSuccessful: Int = 0,

    @Column(name = "groups_failed", nullable = false)
    var groupsFailed: Int = 0,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "scheduled_at")
    val scheduledAt: LocalDateTime? = null,

    @Column(name = "executed_at")
    var executedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

@Entity
@Table(name = "broadcast_group_results")
class BroadcastGroupResult(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "broadcast_id", nullable = false)
    val broadcastId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: WhatsappGroup,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BroadcastGroupSendStatus = BroadcastGroupSendStatus.PENDING,

    @Column(name = "whatsapp_message_id")
    var whatsappMessageId: String? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @Column(name = "processed_at", nullable = false)
    val processedAt: LocalDateTime = LocalDateTime.now()
)
