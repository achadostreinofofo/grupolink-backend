package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "redirect_logs", indexes = [
    Index(name = "idx_redirect_logs_cookie", columnList = "cookieId"),
    Index(name = "idx_redirect_logs_structure", columnList = "structure_id"),
    Index(name = "idx_redirect_logs_redirected_at", columnList = "redirectedAt")
])
class RedirectLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "structure_id", nullable = false)
    val structure: Structure,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: WhatsappGroup,

    @Column(nullable = false)
    val cookieId: String,

    @Column
    val ipAddress: String? = null,

    @Column(columnDefinition = "TEXT")
    val userAgent: String? = null,

    @Column
    val utmSource: String? = null,

    @Column
    val utmMedium: String? = null,

    @Column
    val utmCampaign: String? = null,

    @Column
    val utmContent: String? = null,

    @Column
    val utmTerm: String? = null,

    @Column(nullable = false)
    val redirectedAt: LocalDateTime = LocalDateTime.now()
)
