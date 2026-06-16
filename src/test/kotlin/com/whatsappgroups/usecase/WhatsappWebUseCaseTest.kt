@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.usecase.whatsapp.WhatsappWebUseCase
import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.model.WebSessionStatus
import com.whatsappgroups.domain.model.WhatsappWebSession
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.domain.repository.WhatsappWebSessionRepository
import com.whatsappgroups.infrastructure.whatsapp.WhatsappWebServiceClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsappWebUseCaseTest {

    @Mock private lateinit var sessionRepository: WhatsappWebSessionRepository
    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var serviceClient: WhatsappWebServiceClient

    private lateinit var useCase: WhatsappWebUseCase
    private val userId = UUID.randomUUID()

    private fun setup(plan: Plan, authenticatedCount: Int) {
        useCase = WhatsappWebUseCase(sessionRepository, userRepository, serviceClient)
        val owner = User(id = userId, email = "u@t.com", passwordHash = "h", name = "U", plan = plan)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(owner))
        val sessions = (1..authenticatedCount).map {
            WhatsappWebSession(owner = owner, sessionId = "s$it").apply { status = WebSessionStatus.AUTHENTICATED }
        }
        whenever(sessionRepository.findAllByOwner(owner)).thenReturn(sessions)
        whenever(sessionRepository.save(any()) as WhatsappWebSession?)
            .thenAnswer { it.arguments[0] as WhatsappWebSession }
        whenever(serviceClient.createSession(any<String>())).thenReturn(true)
    }

    @Test
    fun `blocks new session when plan phone limit is reached`() {
        setup(Plan.SMART, authenticatedCount = 2) // Smart = 2

        assertThatThrownBy { useCase.startSession(userId, force = true) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("máximo 2")
        verify(serviceClient, never()).createSession(any())
    }

    @Test
    fun `allows new session when below the plan phone limit`() {
        setup(Plan.DIAMOND, authenticatedCount = 1) // Diamond = 4

        val res = useCase.startSession(userId, force = true)

        assertThat(res.sessionId).isNotBlank()
        verify(serviceClient).createSession(any())
    }
}
