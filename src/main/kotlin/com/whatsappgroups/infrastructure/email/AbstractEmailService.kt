package com.whatsappgroups.infrastructure.email

// Templates de e-mail compartilhados por todos os provedores. As subclasses implementam apenas
// o transporte (`send`); o conteúdo (assunto + HTML) é montado aqui uma única vez.
abstract class AbstractEmailService : EmailService {

    // Transporte específico do provedor (SES, Resend, ...).
    protected abstract fun send(to: String, subject: String, html: String, text: String, replyTo: String? = null)

    override fun sendVerificationEmail(toAddress: String, name: String, activationLink: String) {
        val subject = "Ative sua conta no Redirect Grupo"
        val html = emailLayout(
            preheader = "Bem-vindo ao Redirect Grupo! Clique para ativar sua conta.",
            body = """
                <h1 style="font-size:22px;font-weight:700;color:#111827;margin:0 0 8px">
                    Bem-vindo ao Redirect Grupo, ${escapeHtml(name)}!
                </h1>
                <p style="font-size:15px;color:#6b7280;margin:0 0 24px;line-height:1.6">
                    Sua conta foi criada com sucesso. Para começar a usar a plataforma,
                    confirme seu e-mail clicando no botão abaixo.
                </p>
                ${ctaButton(activationLink, "Ativar minha conta")}
                <p style="font-size:13px;color:#9ca3af;margin:24px 0 0;line-height:1.6">
                    Se você não criou esta conta, ignore este e-mail.<br>
                    O link expira em <strong>24 horas</strong>.
                </p>
                <p style="font-size:12px;color:#d1d5db;margin:16px 0 0">
                    Ou copie o link: <a href="$activationLink" style="color:#0d9488;word-break:break-all">$activationLink</a>
                </p>
            """.trimIndent()
        )
        send(toAddress, subject, html, stripHtml(html))
    }

    override fun sendPasswordResetEmail(toAddress: String, name: String, resetLink: String) {
        val subject = "Redefinição de senha — Redirect Grupo"
        val html = emailLayout(
            preheader = "Recebemos uma solicitação para redefinir sua senha.",
            body = """
                <h1 style="font-size:22px;font-weight:700;color:#111827;margin:0 0 8px">
                    Redefinição de senha
                </h1>
                <p style="font-size:15px;color:#6b7280;margin:0 0 8px;line-height:1.6">
                    Olá, ${escapeHtml(name)}.
                </p>
                <p style="font-size:15px;color:#6b7280;margin:0 0 24px;line-height:1.6">
                    Recebemos uma solicitação para redefinir a senha da sua conta.
                    Clique no botão abaixo para criar uma nova senha.
                </p>
                ${ctaButton(resetLink, "Criar nova senha")}
                <p style="font-size:13px;color:#9ca3af;margin:24px 0 0;line-height:1.6">
                    Se você não solicitou a redefinição, ignore este e-mail — sua senha não será alterada.<br>
                    O link expira em <strong>2 horas</strong>.
                </p>
                <p style="font-size:12px;color:#d1d5db;margin:16px 0 0">
                    Ou copie o link: <a href="$resetLink" style="color:#0d9488;word-break:break-all">$resetLink</a>
                </p>
            """.trimIndent()
        )
        send(toAddress, subject, html, stripHtml(html))
    }

    override fun sendContactEmail(toAddress: String, senderName: String, senderEmail: String, messageBody: String) {
        val subject = "Novo contato via site — $senderName"
        val html = """
            <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px">
              <h2 style="color:#0d9488">Nova mensagem de contato</h2>
              <table style="width:100%;border-collapse:collapse">
                <tr><td style="padding:6px 0;font-weight:bold;width:120px">Nome:</td><td>${escapeHtml(senderName)}</td></tr>
                <tr><td style="padding:6px 0;font-weight:bold">E-mail:</td><td><a href="mailto:${escapeHtml(senderEmail)}">${escapeHtml(senderEmail)}</a></td></tr>
              </table>
              <hr style="margin:16px 0;border:none;border-top:1px solid #e5e7eb"/>
              <h3 style="margin-bottom:8px">Mensagem:</h3>
              <p style="background:#f9fafb;border-left:4px solid #0d9488;padding:12px 16px;border-radius:4px;white-space:pre-wrap">${escapeHtml(messageBody)}</p>
              <p style="font-size:11px;color:#9ca3af;margin-top:24px">Enviado via formulário em redirectgrupo.com.br</p>
            </body></html>
        """.trimIndent()
        val text = "De: $senderName <$senderEmail>\n\n$messageBody"
        send(toAddress, subject, html, text, replyTo = senderEmail)
    }

    // ── templates compartilhados ─────────────────────────────────────────────

    protected fun emailLayout(preheader: String, body: String) = """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Redirect Grupo</title></head>
        <body style="margin:0;padding:0;background:#f3f4f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif">
          <div style="display:none;max-height:0;overflow:hidden;color:#f3f4f6;font-size:1px">$preheader</div>
          <table width="100%" cellpadding="0" cellspacing="0" style="background:#f3f4f6;padding:40px 16px">
            <tr><td align="center">
              <table width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">
                <!-- Header -->
                <tr><td style="background:#0d9488;border-radius:12px 12px 0 0;padding:28px 40px;text-align:center">
                  <span style="font-size:24px;font-weight:800;color:#ffffff;letter-spacing:-0.5px">
                    Redirect&nbsp;<span style="font-weight:400">Grupo</span>
                  </span>
                </td></tr>
                <!-- Body -->
                <tr><td style="background:#ffffff;padding:40px;border-radius:0 0 12px 12px">
                  $body
                  <hr style="margin:32px 0;border:none;border-top:1px solid #f3f4f6"/>
                  <p style="font-size:12px;color:#9ca3af;margin:0;line-height:1.6;text-align:center">
                    Redirect Grupo · <a href="https://www.redirectgrupo.com.br" style="color:#0d9488;text-decoration:none">redirectgrupo.com.br</a><br>
                    Você está recebendo este e-mail porque se cadastrou na plataforma Redirect Grupo.
                  </p>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body></html>
    """.trimIndent()

    protected fun ctaButton(link: String, text: String) = """
        <table cellpadding="0" cellspacing="0" style="margin:0 0 8px">
          <tr><td style="background:#0d9488;border-radius:8px;text-align:center">
            <a href="$link" style="display:inline-block;padding:14px 32px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:8px;letter-spacing:0.2px">
              $text
            </a>
          </td></tr>
        </table>
    """.trimIndent()

    protected fun escapeHtml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    protected fun stripHtml(html: String) = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
}
