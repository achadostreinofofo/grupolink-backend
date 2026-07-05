package com.whatsappgroups.infrastructure.security

import com.whatsappgroups.infrastructure.oauth2.OAuth2SuccessHandler
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import java.time.Duration

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthenticationFilter,
    private val adminJwtFilter: AdminJwtFilter,
    private val oauth2SuccessHandler: OAuth2SuccessHandler,
    @Value("\${app.oauth2.google.client-id:}") private val googleClientId: String
) {
    @Autowired(required = false)
    private var clientRegistrationRepository: ClientRegistrationRepository? = null

    private val googleOAuth2Enabled get() = googleClientId.isNotBlank()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { sm ->
                val policy = if (googleOAuth2Enabled)
                    SessionCreationPolicy.IF_REQUIRED
                else
                    SessionCreationPolicy.STATELESS
                sm.sessionCreationPolicy(policy)
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/admin/auth/login").permitAll()
                    .requestMatchers("/api/auth/signup", "/api/auth/login").permitAll()
                    .requestMatchers("/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/reset-password/validate").permitAll()
                    .requestMatchers("/api/security/public-key").permitAll()
                    .requestMatchers("/api/contact").permitAll()
                    .requestMatchers(HttpMethod.GET, "/r/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/s/**").permitAll()
                    .requestMatchers("/api/webhooks/**").permitAll()
                    .requestMatchers("/api/ml/oauth/callback").permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                    .requestMatchers("/api/oauth2/callback/**").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                    .anyRequest().authenticated()
            }
            // Requests de API sem auth → 401 JSON (nunca redirecionar para OAuth)
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { request, response, _ ->
                    if (request.requestURI.startsWith("/api/") || request.requestURI.startsWith("/r/")) {
                        response.status = HttpServletResponse.SC_UNAUTHORIZED
                        response.contentType = "application/json"
                        response.writer.write("""{"error":"Unauthorized","message":"Token inválido ou expirado"}""")
                    } else {
                        response.sendRedirect("/login")
                    }
                }
            }
            .addFilterBefore(adminJwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)

        // Só ativa Google OAuth se o client-id estiver configurado
        if (googleOAuth2Enabled && clientRegistrationRepository != null) {
            val restTemplate = RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(30))
                .build()
            val tokenResponseClient = DefaultAuthorizationCodeTokenResponseClient()
            tokenResponseClient.setRestOperations(restTemplate)

            http.oauth2Login { oauth2 ->
                oauth2.successHandler(oauth2SuccessHandler)
                oauth2.redirectionEndpoint { it.baseUri("/api/oauth2/callback/*") }
                oauth2.tokenEndpoint { it.accessTokenResponseClient(tokenResponseClient) }
            }
        }

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager =
        config.authenticationManager
}
