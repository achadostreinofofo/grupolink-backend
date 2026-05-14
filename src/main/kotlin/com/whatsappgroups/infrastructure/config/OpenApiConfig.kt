package com.whatsappgroups.infrastructure.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title       = "GrupoLink API",
        version     = "1.0",
        description = """
            API pública do GrupoLink — plataforma SaaS para gerenciamento automático de
            grupos WhatsApp com sistema de afiliados.

            **Autenticação:** Use o endpoint `/api/auth/login` para obter um token JWT
            e clique em **Authorize** (cadeado) para autenticar as requisições.

            Disponível para assinantes do **Plano Black**.
        """,
        contact = Contact(name = "Suporte GrupoLink", email = "suporte@grupolink.com")
    ),
    servers = [
        Server(url = "http://localhost:8080", description = "Desenvolvimento local"),
        Server(url = "https://api.grupolink.com", description = "Produção")
    ],
    security = [SecurityRequirement(name = "Bearer JWT")]
)
@SecurityScheme(
    name         = "Bearer JWT",
    type         = SecuritySchemeType.HTTP,
    scheme       = "bearer",
    bearerFormat = "JWT",
    description  = "Token JWT obtido em POST /api/auth/login"
)
class OpenApiConfig
