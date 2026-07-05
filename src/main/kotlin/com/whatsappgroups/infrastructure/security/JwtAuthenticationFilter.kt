package com.whatsappgroups.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userDetailsService: UserDetailsServiceImpl
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/api/admin/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        extractToken(request)?.let { token ->
            if (jwtTokenProvider.isValid(token)) {
                jwtTokenProvider.extractUserId(token)?.let { userId ->
                    try {
                        val userDetails = userDetailsService.loadUserByUsername(userId.toString())

                        // Invalida tokens emitidos antes da última troca de senha (reset).
                        val changedAt = (userDetails as? AppUserDetails)?.passwordChangedAt
                        val issuedAt  = jwtTokenProvider.extractIssuedAt(token)
                        if (changedAt != null && issuedAt != null && issuedAt.isBefore(changedAt)) {
                            SecurityContextHolder.clearContext()
                        } else {
                            UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities).also {
                                it.details = WebAuthenticationDetailsSource().buildDetails(request)
                                SecurityContextHolder.getContext().authentication = it
                            }
                        }
                    } catch (_: UsernameNotFoundException) {
                        // Token válido mas usuário removido — segue sem autenticar (Spring retorna 401 se necessário)
                        SecurityContextHolder.clearContext()
                    }
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun extractToken(request: HttpServletRequest): String? =
        request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)
}
