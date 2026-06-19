package com.whatsappgroups.application.dto

import jakarta.validation.constraints.NotBlank

data class CreateStructureRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val maxMembersPerGroup: Int = 256,
    val fillThreshold: Double = 0.80,
    // Regras de agendamento (horas em "HH:mm"). Defaults: 08:00–18:00, 5 min.
    val scheduleWindowStart: String = "08:00",
    val scheduleWindowEnd: String = "18:00",
    val scheduleIntervalMinutes: Int = 5
)

// Edição das regras de agendamento de uma estrutura existente.
data class UpdateScheduleConfigRequest(
    val scheduleWindowStart: String,
    val scheduleWindowEnd: String,
    val scheduleIntervalMinutes: Int
)

// Um horário da grade de agendamento de um dia.
// status: AVAILABLE (livre) · TAKEN (já agendado) · PAST (já passou)
data class ScheduleSlotResponse(
    val time: String,        // "HH:mm"
    val datetime: String,    // "yyyy-MM-ddTHH:mm" (horário de Brasília)
    val available: Boolean,
    val status: String
)

data class AddGroupRequest(
    @field:NotBlank val name: String,
    val startingNumber: Int = 1,
    val profilePicUrl: String? = null,
    val participantJids: List<String>? = null
)

// Importa um ou mais grupos existentes de uma vez. maxMembersPerGroup/fillThreshold são da
// estrutura (valor único); o limite efetivo nunca fica abaixo do grupo mais cheio importado.
data class ImportGroupsRequest(
    val whatsappGroupIds: List<String>,
    val maxMembersPerGroup: Int? = null,    // null = mantém padrão da estrutura (256)
    val fillThreshold: Double? = null       // null = mantém padrão (0.80); range 0.1–0.99
)

data class FailedImport(
    val whatsappGroupId: String,
    val reason: String
)

data class ImportGroupsResponse(
    val imported: List<GroupResponse>,
    val failed: List<FailedImport>
)

data class GroupResponse(
    val id: String,
    val name: String,
    val inviteLink: String?,
    val memberCount: Int,
    val maxMembers: Int,
    val capacityPercentage: Double,
    val clickCount: Long,
    val status: String,
    val sortOrder: Int,
    val whatsappGroupId: String?   // null = grupo não criado no WhatsApp ainda
)

data class StructureResponse(
    val id: String,
    val name: String,
    val slug: String,
    val description: String?,
    val maxMembersPerGroup: Int,
    val fillThreshold: Double,
    val active: Boolean,
    val groups: List<GroupResponse>,
    val smartLink: String,
    val groupNamePrefix: String?,
    val nextGroupNumber: Int,
    val groupProfilePicUrl: String?,
    val maxGroupsPerStructure: Int? = null,  // null = ilimitado (plano Black / conta da plataforma)
    val scheduleWindowStart: String = "08:00",
    val scheduleWindowEnd: String = "18:00",
    val scheduleIntervalMinutes: Int = 5
)
