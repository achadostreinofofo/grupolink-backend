package com.whatsappgroups.application.usecase.message

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.dto.GenerateMessageResponse
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.infrastructure.ai.GeminiClient
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GenerateMessageFromLinkUseCase(
    private val mlApiClient: MercadoLivreApiClient,
    private val geminiClient: GeminiClient,
    private val mlAccountUseCase: MercadoLivreAccountUseCase
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(userId: UUID, request: GenerateMessageRequest): GenerateMessageResponse {
        val url = request.productUrl.trim()

        if (!isMercadoLivreUrl(url)) {
            throw IllegalArgumentException("Apenas links do Mercado Livre são aceitos")
        }

        val resolvedUrl = if (url.contains("meli.la", ignoreCase = true)) {
            log.info("Resolving short link: $url")
            mlApiClient.resolveShortLink(url)
        } else url

        val mlbId = extractMlbId(resolvedUrl)?.also { log.info("MLB ID from URL: $it") }
            ?: mlApiClient.extractMlbIdFromPageHtml(resolvedUrl)
            ?: throw IllegalArgumentException("Link não corresponde a um produto específico do Mercado Livre")

        log.info("Fetching ML product: $mlbId (resolved from $url)")

        // The items/products APIs require authentication, so a connected ML account is mandatory.
        val accessToken = mlAccountUseCase.getValidAccessToken(userId)
            ?: throw IllegalStateException("Conecte sua conta do Mercado Livre nas integrações para gerar o texto a partir do link.")

        // Resolve title + price from multiple sources:
        //  1) /items/{id}     → works for individual listings
        //  2) /products/{id}  → catalog products (where /items returns 403)
        //  3) og:title (HTML) → last resort for the title, so we can still generate without price
        var title: String? = null
        var price: Double? = null
        var originalPrice: Double? = null

        mlApiClient.getItem(mlbId, accessToken)?.let { item ->
            title = item.title.takeIf { it.isNotBlank() }
            price = item.price
            originalPrice = item.originalPrice
        }

        // /sale_price retorna amount (preço atual) e regular_amount (preço riscado), calculados
        // de múltiplas fontes pelo ML. Chamado sempre que preço ou desconto ainda está ausente:
        // cobre tanto itens de vitrine social (price=null após /items) quanto itens sem promoção
        // formal (originalPrice=null). Falha silenciosamente se o ID for de catálogo.
        if (price == null || originalPrice == null) {
            mlApiClient.getItemSalePrice(mlbId, accessToken)?.let { sp ->
                if (price == null && sp.amount != null) price = sp.amount
                if (originalPrice == null && sp.regularAmount != null) originalPrice = sp.regularAmount
            }
        }

        if (title == null || price == null) {
            mlApiClient.getCatalogProduct(mlbId, accessToken)?.let { product ->
                if (title == null) title = product.name?.takeIf { it.isNotBlank() }
                if (price == null) price = product.buyBoxWinner?.price
                if (originalPrice == null) originalPrice = product.buyBoxWinner?.originalPrice
            }
        }

        if (title == null) {
            title = mlApiClient.extractTitleFromPage(resolvedUrl)
        }

        val finalTitle = title?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Não foi possível obter as informações do produto. Verifique se o link é válido e tente novamente.")

        val finalPrice = price
        val prompt = buildPrompt(
            title         = finalTitle,
            price         = finalPrice,
            originalPrice = originalPrice?.takeIf { finalPrice != null && it > finalPrice }
        )

        val content = geminiClient.generateText(prompt)
            ?: throw IllegalStateException("Não foi possível gerar o texto no momento. Tente novamente em alguns segundos.")

        log.info("Generated message for product $mlbId ($finalTitle, price=${finalPrice ?: "n/d"})")
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

    private fun buildPrompt(title: String, price: Double?, originalPrice: Double?): String {
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
            when {
                price != null && originalPrice != null -> {
                    val priceFormatted = "R$ %.2f".format(price).replace('.', ',')
                    val originalFormatted = "R$ %.2f".format(originalPrice).replace('.', ',')
                    val discountPct = ((originalPrice - price) / originalPrice * 100).toInt()
                    appendLine("Preço original: $originalFormatted")
                    appendLine("Preço com desconto: $priceFormatted ($discountPct% OFF)")
                }
                price != null -> {
                    appendLine("Preço: ${"R$ %.2f".format(price).replace('.', ',')}")
                }
                // Sem preço: a IA gera a mensagem só com base no nome do produto.
            }
            appendLine()
            appendLine("Retorne APENAS o texto da mensagem, sem aspas ou explicações adicionais.")
        }
    }
}
