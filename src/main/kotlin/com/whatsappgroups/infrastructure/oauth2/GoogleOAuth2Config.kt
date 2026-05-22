package com.whatsappgroups.infrastructure.oauth2

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import java.time.Duration

@Configuration
@ConditionalOnExpression("'\${app.oauth2.google.client-id:}' != ''")
class GoogleOAuth2Config(
    @Value("\${app.oauth2.google.client-id}") private val clientId: String,
    @Value("\${app.oauth2.google.client-secret}") private val clientSecret: String,
    @Value("\${app.oauth2.google.redirect-uri:https://redirectgrupo.com.br/login/oauth2/code/google}")
    private val redirectUri: String
) {

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(googleClientRegistration())

    @Bean
    fun googleOAuth2AccessTokenResponseClient(): OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
        val restTemplate = RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(10))
            .setReadTimeout(Duration.ofSeconds(30))
            .build()
        return DefaultAuthorizationCodeTokenResponseClient().apply {
            setRestOperations(restTemplate)
        }
    }

    private fun googleClientRegistration(): ClientRegistration =
        ClientRegistration.withRegistrationId("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri)
            .scope("openid", "email", "profile")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://www.googleapis.com/oauth2/v4/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .clientName("Google")
            .build()
}
