package com.whatsappgroups.interfaces.api

import com.whatsappgroups.infrastructure.email.SesEmailService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ContactRequest(
    @field:NotBlank(message = "Nome obrigatório")
    @field:Size(min = 2, max = 100)
    val name: String,

    @field:NotBlank(message = "E-mail obrigatório")
    @field:Email(message = "E-mail inválido")
    val email: String,

    @field:NotBlank(message = "Mensagem obrigatória")
    @field:Size(min = 10, max = 2000, message = "A mensagem deve ter entre 10 e 2000 caracteres")
    val message: String
)

@RestController
@RequestMapping("/api/contact")
class ContactController(
    private val sesEmailService: SesEmailService,
    @Value("\${app.contact.email:contato@redirectgrupo.com.br}") private val contactEmail: String
) {

    @PostMapping
    fun send(@Valid @RequestBody request: ContactRequest): ResponseEntity<Map<String, String>> {
        sesEmailService.sendContactEmail(
            toAddress   = contactEmail,
            senderName  = request.name,
            senderEmail = request.email,
            messageBody = request.message
        )
        return ResponseEntity.ok(mapOf("message" to "Mensagem enviada com sucesso!"))
    }
}
