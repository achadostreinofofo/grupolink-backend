package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.LoginRequest
import com.whatsappgroups.application.dto.SignUpRequest
import com.whatsappgroups.application.usecase.auth.AuthUseCase
import com.whatsappgroups.application.usecase.auth.CpfAlreadyExistsException
import com.whatsappgroups.application.usecase.auth.EmailAlreadyExistsException
import com.whatsappgroups.application.usecase.auth.EmailNotVerifiedException
import com.whatsappgroups.domain.model.Plan
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.model.UserStatus
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.email.SesEmailService
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AuthUseCaseTest {

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var emailService: SesEmailService
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var useCase: AuthUseCase

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        passwordEncoder = BCryptPasswordEncoder()
        jwtTokenProvider = JwtTokenProvider(
            secret = "test-secret-at-least-256-bits-long-for-hmac-sha256-algorithm-padding",
            expirationMs = 3600000L
        )
        useCase = AuthUseCase(userRepository, passwordEncoder, jwtTokenProvider, emailService, "http://localhost:3000")
    }

    @Test
    fun `signUp succeeds returns pending response and sends email`() {
        whenever(userRepository.existsByEmail("new@test.com")).thenReturn(false)
        whenever(userRepository.save(any<User>())).thenAnswer { invocation ->
            (invocation.getArgument(0) as User).also {
                val field = it.javaClass.getDeclaredField("id")
                field.isAccessible = true
                field.set(it, userId)
            }
        }

        val result = useCase.signUp(SignUpRequest(email = "new@test.com", password = "pass1234", name = "John"))

        assertThat(result.email).isEqualTo("new@test.com")
        assertThat(result.message).isNotBlank()
        verify(userRepository).save(any())
        verify(emailService).sendVerificationEmail(eq("new@test.com"), eq("John"), any())
    }

    @Test
    fun `signUp with CPF formats and saves correctly`() {
        whenever(userRepository.existsByEmail(any())).thenReturn(false)
        whenever(userRepository.existsByCpf(any())).thenReturn(false)
        whenever(userRepository.save(any<User>())).thenAnswer { inv ->
            (inv.getArgument(0) as User).also {
                val f = it.javaClass.getDeclaredField("id"); f.isAccessible = true; f.set(it, userId)
            }
        }

        useCase.signUp(SignUpRequest(email = "a@b.com", password = "pass1234", name = "Jane", cpf = "123.456.789-09"))

        val captor = argumentCaptor<User>()
        verify(userRepository).save(captor.capture())
        assertThat(captor.firstValue.cpf).isEqualTo("123.456.789-09")
        assertThat(captor.firstValue.status).isEqualTo(UserStatus.PENDING_VERIFICATION)
    }

    @Test
    fun `signUp saves phone when provided`() {
        whenever(userRepository.existsByEmail(any())).thenReturn(false)
        whenever(userRepository.save(any<User>())).thenAnswer { inv ->
            (inv.getArgument(0) as User).also {
                val f = it.javaClass.getDeclaredField("id"); f.isAccessible = true; f.set(it, userId)
            }
        }

        useCase.signUp(SignUpRequest(email = "x@x.com", password = "pass1234", name = "X", phone = "(11) 99999-9999"))

        val captor = argumentCaptor<User>()
        verify(userRepository).save(captor.capture())
        assertThat(captor.firstValue.phone).isEqualTo("11999999999")
    }

    @Test
    fun `signUp throws EmailAlreadyExistsException when email taken`() {
        whenever(userRepository.existsByEmail("dup@test.com")).thenReturn(true)

        assertThatThrownBy {
            useCase.signUp(SignUpRequest(email = "dup@test.com", password = "pass", name = "X"))
        }.isInstanceOf(EmailAlreadyExistsException::class.java)

        verify(userRepository, never()).save(any())
    }

    @Test
    fun `signUp throws CpfAlreadyExistsException when CPF taken`() {
        whenever(userRepository.existsByEmail(any())).thenReturn(false)
        whenever(userRepository.existsByCpf(any())).thenReturn(true)

        assertThatThrownBy {
            useCase.signUp(SignUpRequest(email = "x@x.com", password = "pass", name = "X", cpf = "12345678909"))
        }.isInstanceOf(CpfAlreadyExistsException::class.java)
    }

    @Test
    fun `login succeeds with correct credentials for active user`() {
        val hash = passwordEncoder.encode("secret123")
        val user = user(email = "u@test.com", passwordHash = hash, status = UserStatus.ACTIVE)
        whenever(userRepository.findByEmail("u@test.com")).thenReturn(user)

        val result = useCase.login(LoginRequest(email = "u@test.com", password = "secret123"))

        assertThat(result.email).isEqualTo("u@test.com")
        assertThat(result.token).isNotBlank()
    }

    @Test
    fun `login throws EmailNotVerifiedException for pending user`() {
        val hash = passwordEncoder.encode("secret123")
        val user = user(email = "u@test.com", passwordHash = hash, status = UserStatus.PENDING_VERIFICATION)
        whenever(userRepository.findByEmail("u@test.com")).thenReturn(user)

        assertThatThrownBy {
            useCase.login(LoginRequest(email = "u@test.com", password = "secret123"))
        }.isInstanceOf(EmailNotVerifiedException::class.java)
    }

    @Test
    fun `login throws on wrong password`() {
        val user = user(email = "u@test.com", passwordHash = passwordEncoder.encode("correct"))
        whenever(userRepository.findByEmail("u@test.com")).thenReturn(user)

        assertThatThrownBy {
            useCase.login(LoginRequest(email = "u@test.com", password = "wrong"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("inválidas")
    }

    @Test
    fun `login throws when user not found`() {
        whenever(userRepository.findByEmail(any())).thenReturn(null)

        assertThatThrownBy {
            useCase.login(LoginRequest(email = "ghost@test.com", password = "any"))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun user(
        email: String = "test@test.com",
        passwordHash: String = "hash",
        name: String = "Test",
        status: UserStatus = UserStatus.ACTIVE
    ) = User(id = userId, email = email, passwordHash = passwordHash, name = name, status = status)
}
