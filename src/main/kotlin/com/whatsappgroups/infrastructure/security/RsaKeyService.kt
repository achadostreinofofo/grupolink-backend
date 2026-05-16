package com.whatsappgroups.infrastructure.security

import org.springframework.stereotype.Service
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.util.Base64
import javax.crypto.Cipher

@Service
class RsaKeyService {

    val publicKeyBase64: String
    private val privateKey: PrivateKey

    init {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        privateKey = kp.private
        // SPKI format — directly importable by Web Crypto API (SubjectPublicKeyInfo)
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.public.encoded)
    }

    fun decrypt(encryptedBase64: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val raw = Base64.getDecoder().decode(encryptedBase64)
        return String(cipher.doFinal(raw), Charsets.UTF_8)
    }
}
