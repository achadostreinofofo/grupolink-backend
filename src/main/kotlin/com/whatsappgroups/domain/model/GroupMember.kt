package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

enum class MemberSource { WEBHOOK, REDIRECT }

@Entity
@Table(name = "group_members", indexes = [
    Index(name = "idx_group_members_group",  columnList = "group_id"),
    Index(name = "idx_group_members_phone",  columnList = "phoneNumber"),
    Index(name = "idx_group_members_active", columnList = "group_id, leftAt")
])
class GroupMember(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    val group: WhatsappGroup,

    // Número de telefone no formato internacional (ex: 5511999999999)
    @Column
    var phoneNumber: String? = null,

    @Column
    var name: String? = null,

    // cookieId do RedirectLog quando entrada via link
    @Column
    val cookieId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: MemberSource = MemberSource.REDIRECT,

    @Column(nullable = false)
    val joinedAt: LocalDateTime = LocalDateTime.now(),

    // null = ainda no grupo
    @Column
    var leftAt: LocalDateTime? = null
) {
    val isActive: Boolean get() = leftAt == null
}
