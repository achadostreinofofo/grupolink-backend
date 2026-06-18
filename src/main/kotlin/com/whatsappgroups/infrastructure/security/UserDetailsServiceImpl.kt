package com.whatsappgroups.infrastructure.security

import com.whatsappgroups.domain.repository.UserRepository
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

// UserDetails próprio que carrega o instante da última troca de senha — usado pelo
// JwtAuthenticationFilter para rejeitar tokens emitidos antes desse instante.
class AppUserDetails(
    private val userId: String,
    private val pwd: String,
    private val auths: Collection<GrantedAuthority>,
    val passwordChangedAt: Instant?,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = auths
    override fun getPassword(): String = pwd
    override fun getUsername(): String = userId
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = true
}

@Service
class UserDetailsServiceImpl(private val userRepository: UserRepository) : UserDetailsService {

    // O "username" aqui é o UUID do usuário (conforme gerado no JwtTokenProvider)
    override fun loadUserByUsername(userId: String): UserDetails {
        val uuid = runCatching { UUID.fromString(userId) }
            .getOrElse { throw UsernameNotFoundException("ID inválido: $userId") }

        val user = userRepository.findById(uuid)
            .orElseThrow { UsernameNotFoundException("Usuário não encontrado: $userId") }

        return AppUserDetails(
            userId            = user.id.toString(),
            pwd               = user.passwordHash,
            auths             = listOf(SimpleGrantedAuthority("ROLE_${user.plan.name}")),
            passwordChangedAt = user.passwordChangedAt?.atZone(ZoneId.systemDefault())?.toInstant(),
        )
    }
}
