package com.whatsappgroups.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AdminJwtFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/admin/") ||
        request.requestURI == "/api/admin/auth/login"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val token = request.getHeader("Authorization")
            ?.takeIf { it.startsWith("Bearer ") }
            ?.substring(7)

        if (token == null || !jwtTokenProvider.isValid(token) || jwtTokenProvider.extractRole(token) != "ADMIN") {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error":"Unauthorized","message":"Acesso restrito a administradores"}""")
            return
        }

        val adminId = jwtTokenProvider.extractUserId(token)
        if (adminId != null) {
            request.setAttribute("adminId", adminId.toString())
        }

        filterChain.doFilter(request, response)
    }
}
