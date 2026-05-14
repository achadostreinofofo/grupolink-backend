package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.usecase.auth.ChangePasswordRequest
import com.whatsappgroups.application.usecase.auth.UpdateProfileRequest
import com.whatsappgroups.application.usecase.auth.UserProfileResponse
import com.whatsappgroups.application.usecase.auth.UserSettingsUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class SetPasswordRequest(val newPassword: String)

@Tag(name = "Perfil do Usuário", description = "Atualizar dados pessoais e senha")
@RestController
@RequestMapping("/api/users/me")
class UserController(private val userSettingsUseCase: UserSettingsUseCase) {

    @Operation(summary = "Retorna o perfil completo do usuário autenticado")
    @GetMapping
    fun getProfile(
        @AuthenticationPrincipal user: UserDetails
    ): ResponseEntity<UserProfileResponse> =
        ResponseEntity.ok(userSettingsUseCase.getProfile(UUID.fromString(user.username)))

    @Operation(summary = "Atualiza nome e e-mail do usuário")
    @PutMapping
    fun updateProfile(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<UserProfileResponse> =
        ResponseEntity.ok(userSettingsUseCase.updateProfile(UUID.fromString(user.username), request))

    @Operation(summary = "Troca a senha (requer senha atual). Para usuários OAuth sem senha, use /set-password.")
    @PutMapping("/password")
    fun changePassword(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<Void> {
        userSettingsUseCase.changePassword(UUID.fromString(user.username), request)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Define senha pela primeira vez (usuários criados via Google OAuth)")
    @PostMapping("/set-password")
    fun setPassword(
        @AuthenticationPrincipal user: UserDetails,
        @RequestBody request: SetPasswordRequest
    ): ResponseEntity<Void> {
        userSettingsUseCase.setPasswordForOAuthUser(UUID.fromString(user.username), request.newPassword)
        return ResponseEntity.noContent().build()
    }
}
