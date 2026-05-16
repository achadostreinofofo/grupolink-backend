package com.whatsappgroups.interfaces.api

import com.whatsappgroups.infrastructure.security.RsaKeyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/security")
class SecurityController(private val rsaKeyService: RsaKeyService) {

    @GetMapping("/public-key")
    fun publicKey(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("publicKey" to rsaKeyService.publicKeyBase64))
}
