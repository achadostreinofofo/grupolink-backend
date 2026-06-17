package com.whatsappgroups.application.usecase.message.extractor

import com.whatsappgroups.infrastructure.amazon.AmazonScraperClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

// Extração de dados de produto da Amazon a partir do HTML da página (a Amazon não tem API
// pública de leitura sem afiliado aprovado). Resolve links curtos amzn.to via redirect e
// parseia título, preço e imagem do markup/JSON embutido.
@Component
class AmazonProductExtractor(
    private val amazonScraperClient: AmazonScraperClient
) : ProductExtractor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(url: String): Boolean =
        url.contains("amazon.com.br", ignoreCase = true) ||
        url.contains("amazon.com", ignoreCase = true) ||
        url.contains("amzn.to", ignoreCase = true)

    override fun extract(url: String, userId: UUID): ExtractedProduct? {
        val html = amazonScraperClient.fetchProductHtml(url) ?: return null

        if (isCaptcha(html)) {
            log.warn("Amazon retornou CAPTCHA/robot check para $url")
            throw IllegalStateException(
                "A Amazon bloqueou a leitura automática deste link no momento. Tente novamente em alguns instantes."
            )
        }

        val title = parseTitle(html) ?: return null
        return ExtractedProduct(
            title         = title,
            price         = parsePrice(html),
            originalPrice = parseOriginalPrice(html),
            imageUrl      = parseImage(html)
        )
    }

    // A página de robot check da Amazon não tem os dados do produto; detectamos para dar um
    // erro claro em vez de "não foi possível obter as informações".
    internal fun isCaptcha(html: String): Boolean =
        html.contains("/errors/validateCaptcha", ignoreCase = true) ||
        html.contains("Type the characters you see in this image", ignoreCase = true) ||
        html.contains("Digite os caracteres", ignoreCase = true) ||
        html.contains("Insira os caracteres que você vê", ignoreCase = true)

    internal fun parseTitle(html: String): String? =
        Regex("""id="productTitle"[^>]*>([^<]+)""")
            .find(html)?.groupValues?.get(1)
            ?.let(::decodeHtmlEntities)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    // Preço atual: vem do JSON do buybox ("priceAmount":329.90) — confiável e já numérico
    // (com ponto decimal), evitando a conversão do texto visível "R$ 329,90".
    internal fun parsePrice(html: String): Double? =
        Regex(""""priceAmount"\s*:\s*([0-9]+\.[0-9]+)""")
            .find(html)?.groupValues?.get(1)?.toDoubleOrNull()

    // Preço "De" (riscado): ainda NÃO calibrado — depende de um link da Amazon com desconto real
    // para identificar o padrão confiável sem confundir com preços de produtos recomendados na
    // página. Por ora retorna null (mensagem sai com "Por R$ x"). TODO: extrair list/basis price.
    internal fun parseOriginalPrice(html: String): Double? = null

    // Imagem principal em alta resolução: data-old-hires é a mais direta; cai para o hiRes do
    // JSON de imagens e, por fim, para o src do landingImage.
    internal fun parseImage(html: String): String? =
        Regex("""data-old-hires="(https://[^"]+)"""").find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?: Regex(""""hiRes"\s*:\s*"(https://m\.media-amazon\.com/images/[^"]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""id="landingImage"[^>]*\bsrc="(https://m\.media-amazon\.com/images/[^"]+)"""").find(html)?.groupValues?.get(1)

    private fun decodeHtmlEntities(s: String): String =
        s.replace("&amp;", "&")
         .replace("&quot;", "\"")
         .replace("&#39;", "'")
         .replace("&lt;", "<")
         .replace("&gt;", ">")
         .replace("&nbsp;", " ")
}
