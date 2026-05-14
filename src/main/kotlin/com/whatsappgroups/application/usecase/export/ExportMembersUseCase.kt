package com.whatsappgroups.application.usecase.export

import com.whatsappgroups.domain.model.GroupMember
import com.whatsappgroups.domain.repository.GroupMemberRepository
import com.whatsappgroups.domain.repository.RedirectLogRepository
import com.whatsappgroups.domain.repository.StructureRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter
import java.util.UUID

private val FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

@Service
class ExportMembersUseCase(
    private val structureRepository: StructureRepository,
    private val groupRepository: WhatsappGroupRepository,
    private val memberRepository: GroupMemberRepository,
    private val redirectLogRepository: RedirectLogRepository
) {

    // Exporta membros rastreados via webhook (com número de telefone)
    fun exportMembersCsv(userId: UUID, structureId: UUID): ByteArray {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val members = memberRepository.findAllByStructureId(structureId)

        val sb = StringBuilder()
        sb.appendLine("telefone,nome,grupo,status,entrada,saida,origem")

        members.forEach { m ->
            sb.appendLine(
                listOf(
                    m.phoneNumber ?: m.cookieId ?: "",
                    m.name ?: "",
                    csvEscape(m.group.name),
                    if (m.isActive) "ativo" else "saiu",
                    m.joinedAt.format(FMT),
                    m.leftAt?.format(FMT) ?: "",
                    m.source.name.lowercase()
                ).joinToString(",")
            )
        }

        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // Exporta logs de redirecionamento com dados de UTM (analytics de cliques)
    fun exportRedirectsCsv(userId: UUID, structureId: UUID): ByteArray {
        val structure = structureRepository.findById(structureId)
            .orElseThrow { NoSuchElementException("Estrutura não encontrada") }
        if (structure.owner.id != userId) throw IllegalAccessException("Acesso negado")

        val logs = redirectLogRepository.findAllByStructureIdWithDetails(structureId)

        val sb = StringBuilder()
        sb.appendLine("data,grupo,cookie_id,ip,utm_source,utm_medium,utm_campaign,utm_content,utm_term")

        logs.forEach { log ->
            sb.appendLine(
                listOf(
                    log.redirectedAt.format(FMT),
                    csvEscape(log.group.name),
                    log.cookieId,
                    log.ipAddress ?: "",
                    log.utmSource ?: "",
                    log.utmMedium ?: "",
                    log.utmCampaign ?: "",
                    log.utmContent ?: "",
                    log.utmTerm ?: ""
                ).joinToString(",")
            )
        }

        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n'))
            "\"${value.replace("\"", "\"\"")}\""
        else value
}
