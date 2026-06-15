package com.whatsappgroups.interfaces.api

import com.whatsappgroups.application.usecase.redirect.ProcessRedirectUseCase
import com.whatsappgroups.application.usecase.redirect.RedirectContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Tag(name = "Redirecionamento", description = "Endpoint público de smart link com round-robin e cookies")
@RestController
@RequestMapping("/r")
class RedirectController(private val processRedirectUseCase: ProcessRedirectUseCase) {

    companion object {
        private const val COOKIE_NAME = "wg_uid"
        private const val COOKIE_MAX_AGE = 60 * 60 * 24 * 90 // 90 dias
    }

    @GetMapping("/{slug}")
    fun redirect(
        @PathVariable slug: String,
        @RequestParam(required = false) utm_source: String?,
        @RequestParam(required = false) utm_medium: String?,
        @RequestParam(required = false) utm_campaign: String?,
        @RequestParam(required = false) utm_content: String?,
        @RequestParam(required = false) utm_term: String?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        val cookieId = resolveCookieId(request, response)

        val result = processRedirectUseCase.execute(
            RedirectContext(
                structureSlug = slug,
                cookieId = cookieId,
                ipAddress = resolveClientIp(request),
                userAgent = request.getHeader("User-Agent"),
                utmSource = utm_source,
                utmMedium = utm_medium,
                utmCampaign = utm_campaign,
                utmContent = utm_content,
                utmTerm = utm_term
            )
        )

        // Suppress the Referer so the destination opens as a direct navigation (avoids
        // referer-based gates/anti-bot on the target, e.g. Mercado Livre's login wall).
        response.setHeader("Referrer-Policy", "no-referrer")
        response.status = HttpStatus.FOUND.value()
        response.setHeader("Location", result.inviteLink)
    }

    // Recupera cookie existente ou gera UUID novo e persiste no browser
    private fun resolveCookieId(request: HttpServletRequest, response: HttpServletResponse): String {
        val existing = request.cookies?.find { it.name == COOKIE_NAME }?.value
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString()
        Cookie(COOKIE_NAME, newId).apply {
            maxAge = COOKIE_MAX_AGE
            path = "/"
            isHttpOnly = true
            secure = true
        }.let { response.addCookie(it) }

        return newId
    }

    private fun resolveClientIp(request: HttpServletRequest): String? =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.remoteAddr
}
