package com.whatsappgroups.infrastructure.email

// Abstração de envio de e-mail transacional. Implementada por provedor (SES, Resend, ...)
// e selecionada por configuração (app.email.provider). Os templates são compartilhados em
// AbstractEmailService; cada implementação só define o transporte.
interface EmailService {
    fun sendVerificationEmail(toAddress: String, name: String, activationLink: String)
    fun sendPasswordResetEmail(toAddress: String, name: String, resetLink: String)
    fun sendContactEmail(toAddress: String, senderName: String, senderEmail: String, messageBody: String)
}
