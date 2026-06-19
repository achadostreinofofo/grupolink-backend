package com.whatsappgroups.domain.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "structures")
class Structure(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    val owner: User,

    @Column(nullable = false)
    var name: String,

    @Column(unique = true, nullable = false)
    var slug: String,

    @Column
    var description: String? = null,

    @Column(nullable = false)
    var maxMembersPerGroup: Int = 256,

    // 0.80 = inicia preenchimento dos próximos grupos quando o atual atinge 80%
    @Column(nullable = false)
    var fillThreshold: Double = 0.80,

    // Regras de agendamento (só para mensagens agendadas; envio instantâneo não é afetado).
    // A grade de horários disponíveis vai de scheduleWindowStart a scheduleWindowEnd,
    // de scheduleIntervalMinutes em scheduleIntervalMinutes.
    @Column(name = "schedule_window_start", nullable = false)
    var scheduleWindowStart: LocalTime = LocalTime.of(8, 0),

    @Column(name = "schedule_window_end", nullable = false)
    var scheduleWindowEnd: LocalTime = LocalTime.of(18, 0),

    @Column(name = "schedule_interval_minutes", nullable = false)
    var scheduleIntervalMinutes: Int = 5,

    @Column(nullable = false)
    var active: Boolean = true,

    // Padrão de nomeação dos grupos gerados automaticamente
    @Column
    var groupNamePrefix: String? = null,        // ex: "Achados Treino Fofo"

    @Column(nullable = false)
    var nextGroupNumber: Int = 1,               // próximo número ao criar automaticamente

    @Column
    var groupProfilePicUrl: String? = null,     // foto de perfil usada em todos os grupos

    @OneToMany(mappedBy = "structure", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    val groups: MutableList<WhatsappGroup> = mutableListOf(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
