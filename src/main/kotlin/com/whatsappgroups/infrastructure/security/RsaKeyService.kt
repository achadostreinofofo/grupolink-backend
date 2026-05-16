package com.whatsappgroups.infrastructure.security

import org.springframework.stereotype.Service
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import java.security.spec.PSource

// Web Crypto API (browser) uses RSA-OAEP with SHA-256 for BOTH the content hash AND MGF1.
// Java's "OAEPWithSHA-256AndMGF1Padding" shorthand uses SHA-1 for MGF1 by default,
// causing BadPaddingException on every decryption. OAEPParameterSpec fixes this explicitly.
private val OAEP_PARAMS = OAEPParameterSpec(
    "SHA-256",
    "MGF1",
    MGF1ParameterSpec.SHA256,
    PSource.PSpecified.DEFAULT
)

@Service
class RsaKeyService {

    val publicKeyBase64: String
    private val privateKey: PrivateKey

    init {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        privateKey = kp.private
        publicKeyBase64 = Base64.getEncoder().encodeToString(kp.public.encoded)
    }

    fun decrypt(encryptedBase64: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMS)
        val raw = Base64.getDecoder().decode(encryptedBase64)
        return String(cipher.doFinal(raw), Charsets.UTF_8)
    }
}
