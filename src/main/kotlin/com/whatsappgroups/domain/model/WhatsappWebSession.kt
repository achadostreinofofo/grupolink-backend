package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class WebSessionStatus {
    WAITING_SCAN,
    AUTHENTICATED,
    DISCONNECTED
}

@Entity
@Table(name = "whatsapp_web_sessions")
class WhatsappWebSession(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(name = "session_id", nullable = false, unique = true)
    var sessionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: WebSessionStatus = WebSessionStatus.WAITING_SCAN,

    @Column
    var phone: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
