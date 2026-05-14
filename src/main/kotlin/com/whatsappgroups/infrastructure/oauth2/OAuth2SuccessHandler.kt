package com.whatsappgroups.infrastructure.oauth2

import com.whatsappgroups.domain.model.User
import com.whatsappgroups.domain.repository.UserRepository
import com.whatsappgroups.infrastructure.security.JwtTokenProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OAuth2SuccessHandler(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${app.frontend-url}") private val frontendUrl: String
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauth2User = authentication.principal as OAuth2User
        val email = oauth2User.getAttribute<String>("email")
        val name  = oauth2User.getAttribute<String>("name") ?: email ?: "Usuário"

        if (email == null) {
            log.warn("OAuth2 login sem email — recusado")
            response.sendRedirect("$frontendUrl/login?error=no_email")
            return
        }

        val user = userRepository.findByEmail(email)
            ?: userRepository.save(
                User(
                    email        = email,
                    passwordHash = "",   // sem senha: autenticação via OAuth2
                    name         = name
                )
            )

        val token = jwtTokenProvider.generateToken(user.id!!)
        log.info("Login OAuth2 bem-sucedido para ${user.email}")

        response.sendRedirect("$frontendUrl/auth/callback?token=$token")
    }
}
