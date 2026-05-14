package com.whatsappgroups.infrastructure.payment

import com.mercadopago.MercadoPagoConfig
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class MercadoPagoConfiguration(
    @Value("\${app.mercadopago.access-token}") private val accessToken: String
) {

    @PostConstruct
    fun init() {
        if (accessToken.isNotBlank()) {
            MercadoPagoConfig.setAccessToken(accessToken)
        }
    }
}
