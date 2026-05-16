package com.whatsappgroups.infrastructure.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.whatsappgroups.domain.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.Order
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(2)
class EmailVerificationFilter(
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    // Caminhos que NÃO precisam de e-mail verificado
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return path.startsWith("/api/auth/") ||
               path.startsWith("/api/webhooks/") ||
               path.startsWith("/r/") ||
               path.startsWith("/s/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/oauth2/") ||
               path.startsWith("/login/")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth != null && auth.isAuthenticated && auth.principal != null) {
            runCatching {
                val userId = UUID.fromString(auth.name)
                val user = userRepository.findById(userId).orElse(null)
                if (user != null && !user.emailVerified) {
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.write(
                        objectMapper.writeValueAsString(
                            mapOf(
                                "error"   to "EMAIL_NOT_VERIFIED",
                                "message" to "Confirme seu e-mail para acessar este recurso."
                            )
                        )
                    )
                    return
                }
            }
        }
        chain.doFilter(request, response)
    }
}
