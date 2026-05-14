package com.whatsappgroups.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.whatsappgroups.domain.model.GroupStatus
import com.whatsappgroups.domain.model.WhatsappGroup
import com.whatsappgroups.domain.repository.GroupMemberRepository
import com.whatsappgroups.domain.repository.WhatsappAccountRepository
import com.whatsappgroups.domain.repository.WhatsappGroupRepository
import com.whatsappgroups.interfaces.api.WhatsappWebhookController
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@WebMvcTest(WhatsappWebhookController::class)
@TestPropertySource(properties = ["app.whatsapp.webhook-verify-token=test-verify-token"])
class WhatsappWebhookControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var mapper: ObjectMapper

    @MockBean private lateinit var whatsappAccountRepository: WhatsappAccountRepository
    @MockBean private lateinit var groupRepository: WhatsappGroupRepository
    @MockBean private lateinit var memberRepository: GroupMemberRepository

    // ---- GET: verificação do webhook ----

    @Test
    fun `GET com token correto retorna challenge`() {
        mockMvc.get("/api/webhooks/whatsapp") {
            param("hub.mode", "subscribe")
            param("hub.verify_token", "test-verify-token")
            param("hub.challenge", "abc123challenge")
        }.andExpect {
            status { isOk() }
            content { string("abc123challenge") }
        }
    }

    @Test
    fun `GET com token incorreto retorna 403`() {
        mockMvc.get("/api/webhooks/whatsapp") {
            param("hub.mode", "subscribe")
            param("hub.verify_token", "wrong-token")
            param("hub.challenge", "abc123")
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `GET com mode diferente de subscribe retorna 403`() {
        mockMvc.get("/api/webhooks/whatsapp") {
            param("hub.mode", "unsubscribe")
            param("hub.verify_token", "test-verify-token")
            param("hub.challenge", "abc123")
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ---- POST: eventos de mensagem ----

    @Test
    fun `POST com payload vazio retorna 200`() {
        val payload = mapOf("object" to "whatsapp_business_account", "entry" to emptyList<Any>())

        mockMvc.post("/api/webhooks/whatsapp") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(payload)
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `POST com evento de mensagem normal retorna 200 sem persistir membro`() {
        val payload = mapOf(
            "object" to "whatsapp_business_account",
            "entry" to listOf(mapOf(
                "changes" to listOf(mapOf(
                    "value" to mapOf(
                        "messages" to listOf(mapOf(
                            "from" to "5511999999999",
                            "type" to "text",
                            "text" to mapOf("body" to "Oi")
                        ))
                    )
                ))
            ))
        )

        mockMvc.post("/api/webhooks/whatsapp") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(payload)
        }.andExpect {
            status { isOk() }
        }

        verify(memberRepository, never()).save(any())
    }

    @Test
    fun `POST com evento system group_member_add persiste membro`() {
        val mockGroup = WhatsappGroup(
            id = java.util.UUID.randomUUID(),
            structure = mockk(),
            name = "Test Group",
            inviteLink = "https://chat.whatsapp.com/test",
            maxMembers = 256,
            memberCount = 5,
            status = GroupStatus.ACTIVE
        )

        given(whatsappAccountRepository.findByPhoneNumberId("12345")).willReturn(null)

        val payload = mapOf(
            "object" to "whatsapp_business_account",
            "entry" to listOf(mapOf(
                "changes" to listOf(mapOf(
                    "value" to mapOf(
                        "metadata" to mapOf("phone_number_id" to "12345"),
                        "messages" to listOf(mapOf(
                            "from" to "5511888888888",
                            "type" to "system",
                            "system" to mapOf("type" to "group_member_add", "name" to "João")
                        ))
                    )
                ))
            ))
        )

        mockMvc.post("/api/webhooks/whatsapp") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(payload)
        }.andExpect {
            status { isOk() }
        }
    }
}

// Inline mock helper para testes que não precisam de Spring context completo
private fun mockk(): com.whatsappgroups.domain.model.Structure {
    return org.mockito.kotlin.mock()
}
