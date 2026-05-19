package com.whatsappgroups.usecase

import com.whatsappgroups.application.usecase.auth.ChangePasswordRequest
import com.whatsappgroups.application.usecase.auth.UpdateProfileRequest
import com.whatsappgroups.application.usecase.auth.UserSettingsUseCase
import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.repository.UserRepository
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UserSettingsUseCaseTest {

    @Mock private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var useCase: UserSettingsUseCase

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        passwordEncoder = BCryptPasswordEncoder()
        useCase = UserSettingsUseCase(userRepository, passwordEncoder)
    }

    @Test
    fun `getProfile returns user profile`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user()))

        val profile = useCase.getProfile(userId)

        assertThat(profile.email).isEqualTo("test@test.com")
        assertThat(profile.name).isEqualTo("Test User")
        assertThat(profile.hasPassword).isTrue()
    }

    @Test
    fun `getProfile throws when user not found`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertThatThrownBy { useCase.getProfile(userId) }
            .isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `updateProfile changes name without touching email`() {
        val u = user()
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(u))
        // No existsByEmail stub needed — same email, so check is skipped

        val result = useCase.updateProfile(userId, UpdateProfileRequest(name = "New Name", email = "test@test.com"))

        assertThat(result.name).isEqualTo("New Name")
        verify(userRepository, never()).existsByEmail(any())
    }

    @Test
    fun `updateProfile checks for email conflict when email changes`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user()))
        whenever(userRepository.existsByEmail("new@test.com")).thenReturn(false)

        val result = useCase.updateProfile(userId, UpdateProfileRequest(name = "Test User", email = "new@test.com"))

        assertThat(result.email).isEqualTo("new@test.com")
        verify(userRepository).existsByEmail("new@test.com")
    }

    @Test
    fun `updateProfile throws on conflicting email`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user()))
        whenever(userRepository.existsByEmail("taken@test.com")).thenReturn(true)

        assertThatThrownBy {
            useCase.updateProfile(userId, UpdateProfileRequest(name = "X", email = "taken@test.com"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("em uso")
    }

    @Test
    fun `updateProfile throws when user not found`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertThatThrownBy {
            useCase.updateProfile(userId, UpdateProfileRequest(name = "X", email = "x@x.com"))
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `changePassword succeeds with correct current password`() {
        val hash = passwordEncoder.encode("oldPass1")
        val u = user(passwordHash = hash)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(u))

        useCase.changePassword(userId, ChangePasswordRequest(currentPassword = "oldPass1", newPassword = "newPass99"))

        assertThat(passwordEncoder.matches("newPass99", u.passwordHash)).isTrue()
    }

    @Test
    fun `changePassword throws on wrong current password`() {
        val hash = passwordEncoder.encode("correct")
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user(passwordHash = hash)))

        assertThatThrownBy {
            useCase.changePassword(userId, ChangePasswordRequest(currentPassword = "wrong", newPassword = "new123456"))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("incorreta")
    }

    @Test
    fun `changePassword throws for OAuth user with blank hash`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user(passwordHash = "")))

        assertThatThrownBy {
            useCase.changePassword(userId, ChangePasswordRequest(currentPassword = "any", newPassword = "new12345"))
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `changePassword throws when user not found`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertThatThrownBy {
            useCase.changePassword(userId, ChangePasswordRequest(currentPassword = "a", newPassword = "b"))
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    @Test
    fun `setPasswordForOAuthUser sets password when none exists`() {
        val u = user(passwordHash = "")
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(u))

        useCase.setPasswordForOAuthUser(userId, "newPassOAuth1")

        assertThat(passwordEncoder.matches("newPassOAuth1", u.passwordHash)).isTrue()
    }

    @Test
    fun `setPasswordForOAuthUser throws when password already set`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user(passwordHash = "existing-hash")))

        assertThatThrownBy {
            useCase.setPasswordForOAuthUser(userId, "newPass")
        }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `setPasswordForOAuthUser throws when user not found`() {
        whenever(userRepository.findById(userId)).thenReturn(Optional.empty())

        assertThatThrownBy {
            useCase.setPasswordForOAuthUser(userId, "newPass")
        }.isInstanceOf(NoSuchElementException::class.java)
    }

    private fun user(email: String = "test@test.com", passwordHash: String = "hashed-password", name: String = "Test User") =
        User(id = userId, email = email, passwordHash = passwordHash, name = name)
}
