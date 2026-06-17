package com.whatsappgroups.application.usecase.message

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.dto.GenerateMessageResponse
import com.whatsappgroups.application.usecase.message.extractor.ProductExtractor
import com.whatsappgroups.infrastructure.ai.GeminiClient
import com.whatsappgroups.infrastructure.storage.ProductImageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class GenerateMessageFromLinkUseCase(
    private val extractors: List<ProductExtractor>,
    private val geminiClient: GeminiClient,
    private val productImageService: ProductImageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun generate(userId: UUID, request: GenerateMessageRequest): GenerateMessageResponse {
        val url = request.productUrl.trim()

        // Strategy: o primeiro extractor que suporta a URL resolve os dados do produto.
        val extractor = extractors.firstOrNull { it.supports(url) }
            ?: throw IllegalArgumentException("Apenas links do Mercado Livre são aceitos")

        val product = extractor.extract(url, userId)
            ?: throw IllegalStateException("Não foi possível obter as informações do produto. Verifique se o link é válido e tente novamente.")

        val prompt = buildPrompt(
            title         = product.title,
            price         = product.price,
            originalPrice = product.originalPrice?.takeIf { product.price != null && it > product.price }
        )

        val content = geminiClient.generateText(prompt)
            ?: throw IllegalStateException("Não foi possível gerar o texto no momento. Tente novamente em alguns segundos.")

        // Layout final: texto da IA → bloco de preço (determinístico) → link de afiliado.
        val finalContent = buildString {
            append(content.trim())
            buildPriceBlock(product.price, product.originalPrice).takeIf { it.isNotEmpty() }?.let {
                append("\n\n")
                append(it)
            }
            append("\n\n")
            append(url)
        }

        // Persiste a imagem do produto no nosso S3, para que a mídia salva na mensagem seja
        // idêntica a um upload manual. Se o download/upload falhar, cai para a URL original
        // (graceful degradation — a mensagem ainda tem imagem).
        val finalImageUrl = product.imageUrl?.let { productImageService.persist(it, userId) ?: it }

        log.info("Generated message (${product.title}, price=${product.price ?: "n/d"}, imageUrl=${finalImageUrl ?: "n/d"})")
        return GenerateMessageResponse(content = finalContent, imageUrl = finalImageUrl, title = product.title)
    }

    // Bloco de preço inserido na mensagem (após o texto, antes do link):
    //  - com promoção (originalPrice > price): "De R$ x\nPor R$ y"
    //  - só preço:                              "Por R$ y"
    //  - sem preço:                             "" (nada)
    private fun buildPriceBlock(price: Double?, originalPrice: Double?): String {
        if (price == null) return ""
        val hasDiscount = originalPrice != null && originalPrice > price
        return if (hasDiscount) {
            "De ${formatBrl(originalPrice!!)}\nPor ${formatBrl(price)}"
        } else {
            "Por ${formatBrl(price)}"
        }
    }

    private fun formatBrl(value: Double): String =
        "R$ " + String.format(java.util.Locale.US, "%.2f", value).replace('.', ',')

    private fun buildPrompt(title: String, price: Double?, originalPrice: Double?): String {
        return buildString {
            appendLine("Crie uma mensagem de WhatsApp curta e persuasiva em português informal para divulgar o seguinte produto do Mercado Livre em grupos de WhatsApp.")
            appendLine()
            appendLine("Regras obrigatórias:")
            appendLine("- No máximo 5 linhas")
            appendLine("- Use emojis relevantes ao produto")
            appendLine("- Termine com uma frase curta incentivando clicar no link (ex: 'Aproveite 👇', 'Corre lá 🔥')")
            appendLine("- NÃO inclua valores de preço nem o link na mensagem — eles serão adicionados automaticamente")
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
