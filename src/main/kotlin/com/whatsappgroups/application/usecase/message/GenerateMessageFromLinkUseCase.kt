package com.whatsappgroups.application.usecase.message

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.dto.GenerateMessageResponse
import com.whatsappgroups.infrastructure.ai.GeminiClient
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class GenerateMessageFromLinkUseCase(
    private val mlApiClient: MercadoLivreApiClient,
    private val geminiClient: GeminiClient
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(request: GenerateMessageRequest): GenerateMessageResponse {
        val url = request.productUrl.trim()

        if (!isMercadoLivreUrl(url)) {
            throw IllegalArgumentException("Apenas links do Mercado Livre são aceitos")
        }

        val resolvedUrl = if (url.contains("meli.la", ignoreCase = true)) {
            log.info("Resolving short link: $url")
            mlApiClient.resolveShortLink(url)
        } else url

        val (mlbId, idSource) = run {
            extractMlbId(resolvedUrl)?.let { it to "url" }
                ?: mlApiClient.extractMlbIdFromPageHtml(resolvedUrl)?.let { it to "html" }
                ?: throw IllegalArgumentException(
                    "[DEBUG] Nenhum ID MLB encontrado. URL resolvida: ${resolvedUrl.take(200)}"
                )
        }

        log.info("Fetching ML item: $mlbId (source=$idSource)")

        val item = mlApiClient.getItem(mlbId)
            ?: throw IllegalStateException(
                "[DEBUG] API ML sem dados. ID: $mlbId | fonte: $idSource | URL: ${resolvedUrl.take(150)}"
            )

        if (item.title.isBlank() || item.price == null) {
            throw IllegalStateException(
                "[DEBUG] Dados incompletos. ID: $mlbId | título: '${item.title}' | preço: ${item.price}"
            )
        }

        val prompt = buildPrompt(
            title         = item.title,
            price         = item.price,
            originalPrice = item.originalPrice?.takeIf { it > item.price }
        )

        val content = geminiClient.generateText(prompt)
            ?: throw IllegalStateException("Não foi possível gerar o texto no momento. Tente novamente em alguns segundos.")

        log.info("Generated message for item $mlbId (${item.title})")
        return GenerateMessageResponse(content = content.trim())
    }

    private fun isMercadoLivreUrl(url: String): Boolean {
        return url.contains("mercadolivre.com.br", ignoreCase = true) ||
               url.contains("mercadolibre.com", ignoreCase = true) ||
               url.contains("meli.la", ignoreCase = true) ||
               url.contains("produto.mercadolivre", ignoreCase = true)
    }

    private fun extractMlbId(url: String): String? {
        val match = Regex("""MLB-?(\d+)""").find(url) ?: return null
        return "MLB${match.groupValues[1]}"
    }

    private fun buildPrompt(title: String, price: Double, originalPrice: Double?): String {
        val priceFormatted = "R$ %.2f".format(price).replace('.', ',')

        return buildString {
            appendLine("Crie uma mensagem de WhatsApp curta e persuasiva em português informal para divulgar o seguinte produto do Mercado Livre em grupos de WhatsApp.")
            appendLine()
            appendLine("Regras obrigatórias:")
            appendLine("- No máximo 5 linhas")
            appendLine("- Use emojis relevantes ao produto")
            appendLine("- Termine com uma frase curta incentivando clicar no link (ex: 'Aproveite 👇', 'Corre lá 🔥')")
            appendLine("- NÃO inclua o link na mensagem — ele será adicionado automaticamente")
            appendLine("- Escreva em português informal e direto")
            appendLine()
            appendLine("Dados do produto:")
            appendLine("Produto: $title")
            if (originalPrice != null) {
                val originalFormatted = "R$ %.2f".format(originalPrice).replace('.', ',')
                val discountPct = ((originalPrice - price) / originalPrice * 100).toInt()
                appendLine("Preço original: $originalFormatted")
                appendLine("Preço com desconto: $priceFormatted ($discountPct% OFF)")
            } else {
                appendLine("Preço: $priceFormatted")
            }
            appendLine()
            appendLine("Retorne APENAS o texto da mensagem, sem aspas ou explicações adicionais.")
        }
    }
}
