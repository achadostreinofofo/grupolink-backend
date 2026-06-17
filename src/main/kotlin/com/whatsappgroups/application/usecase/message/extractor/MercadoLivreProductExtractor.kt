package com.whatsappgroups.application.usecase.message.extractor

import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

// Extração de dados de produto do Mercado Livre. Resolve links curtos (meli.la), descobre o
// MLB id (da URL ou do HTML de vitrines sociais) e resolve título/preço/imagem de múltiplas
// fontes, na ordem: /items → /sale_price → /products → HTML embutido.
@Component
class MercadoLivreProductExtractor(
    private val mlApiClient: MercadoLivreApiClient,
    private val mlAccountUseCase: MercadoLivreAccountUseCase
) : ProductExtractor {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(url: String): Boolean = isMercadoLivreUrl(url)

    override fun extract(url: String, userId: UUID): ExtractedProduct? {
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
        var imageUrl: String? = null

        mlApiClient.getItem(mlbId, accessToken)?.let { item ->
            title = item.title.takeIf { it.isNotBlank() }
            price = item.price
            originalPrice = item.originalPrice
            imageUrl = item.thumbnail
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

        // Último recurso (e única fonte para vitrines sociais): extrai título, preço, preço
        // riscado e imagem direto do HTML embutido. As APIs acima não resolvem o product_id de
        // afiliado das páginas /social/, mas a página traz todos esses dados.
        if (title == null || price == null || imageUrl == null) {
            mlApiClient.extractProductDataFromPage(resolvedUrl)?.let { page ->
                if (title == null) title = page.title
                if (price == null) price = page.price
                if (originalPrice == null) originalPrice = page.originalPrice
                if (imageUrl == null) imageUrl = page.imageUrl
            }
        }

        val finalTitle = title?.takeIf { it.isNotBlank() } ?: return null
        return ExtractedProduct(
            title         = finalTitle,
            price         = price,
            originalPrice = originalPrice,
            imageUrl      = imageUrl
        )
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
}
