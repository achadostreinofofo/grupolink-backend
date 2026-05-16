package com.whatsappgroups.infrastructure.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from:noreply@redirectgrupo.com.br}") private val from: String,
    @Value("\${app.frontend-url}") private val frontendUrl: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun sendVerificationEmail(toEmail: String, name: String, token: String) {
        val verifyUrl = "$frontendUrl/auth/verify-email?token=$token"
        val subject = "Confirme seu e-mail — Redirect Grupo"
        val html = buildVerificationEmail(name, verifyUrl)

        runCatching {
            val msg = mailSender.createMimeMessage()
            MimeMessageHelper(msg, true, "UTF-8").apply {
                setFrom(from, "Redirect Grupo")
                setTo(toEmail)
                setSubject(subject)
                setText(html, true)
            }
            mailSender.send(msg)
            log.info("E-mail de verificação enviado para $toEmail")
        }.onFailure {
            log.error("Falha ao enviar e-mail de verificação para $toEmail: ${it.message}")
        }
    }

    @Async
    fun sendWelcomeEmail(toEmail: String, name: String) {
        val subject = "Bem-vindo ao Redirect Grupo!"
        val html = buildWelcomeEmail(name)

        runCatching {
            val msg = mailSender.createMimeMessage()
            MimeMessageHelper(msg, true, "UTF-8").apply {
                setFrom(from, "Redirect Grupo")
                setTo(toEmail)
                setSubject(subject)
                setText(html, true)
            }
            mailSender.send(msg)
        }.onFailure {
            log.error("Falha ao enviar e-mail de boas-vindas para $toEmail: ${it.message}")
        }
    }

    private fun buildVerificationEmail(name: String, verifyUrl: String) = """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
        <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 0;">
            <tr><td align="center">
              <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                <!-- Header -->
                <tr>
                  <td style="background:linear-gradient(135deg,#080B14 0%,#0D1117 100%);padding:36px 40px;text-align:center;">
                    <table cellpadding="0" cellspacing="0" style="margin:0 auto;">
                      <tr>
                        <td style="background:linear-gradient(135deg,#00E5FF,#A855F7);border-radius:10px;width:36px;height:36px;text-align:center;vertical-align:middle;">
                          <span style="color:#04070f;font-weight:900;font-size:16px;line-height:36px;">R</span>
                        </td>
                        <td style="padding-left:10px;">
                          <span style="color:#ffffff;font-weight:700;font-size:18px;letter-spacing:-0.3px;">Redirect Grupo</span>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                <!-- Body -->
                <tr>
                  <td style="padding:40px;">
                    <h2 style="margin:0 0 8px;font-size:24px;font-weight:700;color:#0f172a;">Confirme seu e-mail</h2>
                    <p style="margin:0 0 24px;color:#64748b;font-size:15px;line-height:1.6;">
                      Olá, <strong style="color:#0f172a;">${name}</strong>! Obrigado por se cadastrar no Redirect Grupo.
                      Clique no botão abaixo para confirmar seu e-mail e ativar sua conta.
                    </p>
                    <p style="margin:0 0 8px;color:#64748b;font-size:13px;">⏰ Este link expira em <strong>24 horas</strong>.</p>
                    <table cellpadding="0" cellspacing="0" style="margin:32px 0;">
                      <tr>
                        <td style="background:linear-gradient(135deg,#00E5FF,#0097a7);border-radius:10px;">
                          <a href="$verifyUrl"
                             style="display:inline-block;padding:14px 36px;color:#04070f;font-weight:700;font-size:15px;text-decoration:none;border-radius:10px;">
                            ✓ Confirmar e-mail
                          </a>
                        </td>
                      </tr>
                    </table>
                    <p style="margin:24px 0 8px;color:#94a3b8;font-size:12px;">
                      Se o botão não funcionar, copie e cole o link abaixo no seu navegador:
                    </p>
                    <p style="margin:0;background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:10px 14px;font-family:monospace;font-size:11px;color:#475569;word-break:break-all;">
                      $verifyUrl
                    </p>
                    <hr style="margin:32px 0;border:none;border-top:1px solid #f1f5f9;">
                    <p style="margin:0;color:#94a3b8;font-size:12px;line-height:1.6;">
                      Se você não criou uma conta no Redirect Grupo, ignore este e-mail.<br>
                      Dúvidas? <a href="mailto:suporte@redirectgrupo.com.br" style="color:#00bcd4;">suporte@redirectgrupo.com.br</a>
                    </p>
                  </td>
                </tr>
                <!-- Footer -->
                <tr>
                  <td style="background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #f1f5f9;">
                    <p style="margin:0;color:#cbd5e1;font-size:11px;">
                      © ${java.time.Year.now().value} Redirect Grupo. Todos os direitos reservados.
                    </p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()

    private fun buildWelcomeEmail(name: String) = """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head><meta charset="UTF-8"></head>
        <body style="margin:0;padding:0;background:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
          <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:40px 0;">
            <tr><td align="center">
              <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                <tr>
                  <td style="background:linear-gradient(135deg,#080B14 0%,#0D1117 100%);padding:36px 40px;text-align:center;">
                    <span style="color:#ffffff;font-weight:700;font-size:20px;">🎉 Bem-vindo ao Redirect Grupo!</span>
                  </td>
                </tr>
                <tr>
                  <td style="padding:40px;">
                    <p style="margin:0 0 16px;color:#0f172a;font-size:16px;">Olá, <strong>${name}</strong>!</p>
                    <p style="margin:0 0 16px;color:#64748b;font-size:15px;line-height:1.6;">
                      Sua conta foi verificada com sucesso. Você tem <strong>7 dias de acesso gratuito</strong> ao plano Smart.
                    </p>
                    <p style="margin:0 0 24px;color:#64748b;font-size:15px;line-height:1.6;">
                      Comece criando sua primeira estrutura de grupos e veja como é simples gerenciar centenas de membros com um único link.
                    </p>
                    <table cellpadding="0" cellspacing="0">
                      <tr>
                        <td style="background:linear-gradient(135deg,#00E5FF,#0097a7);border-radius:10px;">
                          <a href="${frontendUrl}/dashboard"
                             style="display:inline-block;padding:14px 36px;color:#04070f;font-weight:700;font-size:15px;text-decoration:none;">
                            Acessar meu dashboard →
                          </a>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
                <tr>
                  <td style="background:#f8fafc;padding:20px 40px;text-align:center;border-top:1px solid #f1f5f9;">
                    <p style="margin:0;color:#cbd5e1;font-size:11px;">© ${java.time.Year.now().value} Redirect Grupo</p>
                  </td>
                </tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
    """.trimIndent()
}
