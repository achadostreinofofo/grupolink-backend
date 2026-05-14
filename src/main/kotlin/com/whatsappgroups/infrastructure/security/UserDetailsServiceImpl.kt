package com.whatsappgroups.infrastructure.security

import com.whatsappgroups.domain.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserDetailsServiceImpl(private val userRepository: UserRepository) : UserDetailsService {

    // O "username" aqui é o UUID do usuário (conforme gerado no JwtTokenProvider)
    override fun loadUserByUsername(userId: String): UserDetails {
        val uuid = runCatching { UUID.fromString(userId) }
            .getOrElse { throw UsernameNotFoundException("ID inválido: $userId") }

        val user = userRepository.findById(uuid)
            .orElseThrow { UsernameNotFoundException("Usuário não encontrado: $userId") }

        return User.builder()
            .username(user.id.toString())
            .password(user.passwordHash)
            .authorities(SimpleGrantedAuthority("ROLE_${user.plan.name}"))
            .build()
    }
}
