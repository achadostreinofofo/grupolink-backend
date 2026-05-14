package com.whatsappgroups.interfaces.api

import com.whatsappgroups.domain.model.Blacklist
import com.whatsappgroups.domain.repository.BlacklistRepository
import com.whatsappgroups.domain.repository.UserRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class AddBlacklistRequest(
    @field:NotBlank val phoneNumber: String,
    val reason: String? = null
)

data class BlacklistResponse(
    val id: String,
    val phoneNumber: String,
    val reason: String?,
    val createdAt: String
)

@RestController
@RequestMapping("/api/blacklist")
class BlacklistController(
    private val blacklistRepository: BlacklistRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun list(@AuthenticationPrincipal user: UserDetails): ResponseEntity<List<BlacklistResponse>> {
        val owner = userRepository.getReferenceById(UUID.fromString(user.username))
        return ResponseEntity.ok(
            blacklistRepository.findAllByOwner(owner).map { it.toResponse() }
        )
    }

    @PostMapping
    fun add(
        @AuthenticationPrincipal user: UserDetails,
        @Valid @RequestBody request: AddBlacklistRequest
    ): ResponseEntity<BlacklistResponse> {
        val owner = userRepository.getReferenceById(UUID.fromString(user.username))

        if (blacklistRepository.existsByOwnerAndPhoneNumber(owner, request.phoneNumber)) {
            throw IllegalArgumentException("Número já está na blacklist")
        }

        val entry = blacklistRepository.save(
            Blacklist(owner = owner, phoneNumber = request.phoneNumber, reason = request.reason)
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(entry.toResponse())
    }

    @DeleteMapping("/{id}")
    fun remove(
        @AuthenticationPrincipal user: UserDetails,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        val entry = blacklistRepository.findById(id)
            .orElseThrow { NoSuchElementException("Entrada não encontrada") }
        if (entry.owner.id != UUID.fromString(user.username)) throw IllegalAccessException("Acesso negado")
        blacklistRepository.delete(entry)
        return ResponseEntity.noContent().build()
    }

    private fun Blacklist.toResponse() = BlacklistResponse(
        id          = id.toString(),
        phoneNumber = phoneNumber,
        reason      = reason,
        createdAt   = createdAt.toString()
    )
}
