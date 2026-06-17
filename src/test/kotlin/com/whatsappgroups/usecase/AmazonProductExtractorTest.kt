@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.usecase.message.extractor.AmazonProductExtractor
import com.whatsappgroups.infrastructure.amazon.AmazonScraperClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmazonProductExtractorTest {

    @Mock private lateinit var scraperClient: AmazonScraperClient

    private lateinit var extractor: AmazonProductExtractor
    private val userId = UUID.randomUUID()

    private fun setup() {
        extractor = AmazonProductExtractor(scraperClient)
    }

    // Trecho representativo de uma página de produto real da Amazon BR.
    private val productHtml = """
        <html><head><title>Amazon.com.br</title></head><body>
        <h1><span id="productTitle" class="a-size-large product-title-word-break">        Whey Protein Isolado Doce de Leite Pote 900g – DUX HUMAN HEALTH       </span></h1>
        <div id="corePriceDisplay">
          <span class="a-price"><span class="a-offscreen">R${'$'}329,90</span></span>
        </div>
        <script type="a-state" data-a-state='{"key":"desktop-buying-options-price-data"}'>{"desktop_buybox_group_1":[{"displayPrice":"R${'$'} 329,90","priceAmount":329.90,"currencySymbol":"R${'$'}"}]}</script>
        <img id="landingImage" data-old-hires="https://m.media-amazon.com/images/I/51MqPk9sInL._AC_SL1000_.jpg" src="https://m.media-amazon.com/images/I/51MqPk9sInL._AC_SY355_.jpg"/>
        </body></html>
    """.trimIndent()

    @Test
    fun `supports Amazon URLs and short links, rejects others`() {
        setup()
        assertThat(extractor.supports("https://www.amazon.com.br/dp/B0D1YMT95B")).isTrue()
        assertThat(extractor.supports("https://amzn.to/4vbJhw5")).isTrue()
        assertThat(extractor.supports("https://www.amazon.com/dp/X")).isTrue()
        assertThat(extractor.supports("https://produto.mercadolivre.com.br/MLB-1")).isFalse()
        assertThat(extractor.supports("https://meli.la/x")).isFalse()
    }

    @Test
    fun `extracts title, price and image from a product page`() {
        setup()
        whenever(scraperClient.fetchProductHtml(any())).thenReturn(productHtml)

        val p = extractor.extract("https://amzn.to/4vbJhw5", userId)!!

        assertThat(p.title).isEqualTo("Whey Protein Isolado Doce de Leite Pote 900g – DUX HUMAN HEALTH")
        assertThat(p.price).isEqualTo(329.90)
        assertThat(p.originalPrice).isNull()
        assertThat(p.imageUrl).isEqualTo("https://m.media-amazon.com/images/I/51MqPk9sInL._AC_SL1000_.jpg")
    }

    @Test
    fun `returns null when the page has no product (e g not found)`() {
        setup()
        whenever(scraperClient.fetchProductHtml(any())).thenReturn("<html><body>Página não encontrada</body></html>")

        assertThat(extractor.extract("https://www.amazon.com.br/dp/X", userId)).isNull()
    }

    @Test
    fun `returns null when the fetch fails`() {
        setup()
        whenever(scraperClient.fetchProductHtml(any())).thenReturn(null)

        assertThat(extractor.extract("https://amzn.to/abc", userId)).isNull()
    }

    @Test
    fun `throws a friendly error when Amazon returns a captcha page`() {
        setup()
        whenever(scraperClient.fetchProductHtml(any())).thenReturn(
            """<html><body><form action="/errors/validateCaptcha" method="get">Type the characters you see in this image</form></body></html>"""
        )

        assertThatThrownBy { extractor.extract("https://amzn.to/abc", userId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Amazon bloqueou")
    }

    @Test
    fun `throws a friendly error on a Robot Check page`() {
        setup()
        whenever(scraperClient.fetchProductHtml(any())).thenReturn(
            """<html><head><title>Robot Check</title></head><body>we just need to make sure you're not a robot</body></html>"""
        )

        assertThatThrownBy { extractor.extract("https://amzn.to/abc", userId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Amazon bloqueou")
    }

    @Test
    fun `falls back to the page title when productTitle is absent`() {
        setup()
        whenever(scraperClient.fetchProductHtml(any())).thenReturn(
            """<html><head><title>Whey Protein Isolado 900g – DUX | Amazon.com.br</title></head>
               <body><script>"priceAmount":329.90</script></body></html>"""
        )

        val p = extractor.extract("https://www.amazon.com.br/dp/X", userId)!!

        assertThat(p.title).isEqualTo("Whey Protein Isolado 900g – DUX")
        assertThat(p.price).isEqualTo(329.90)
    }

    @Test
    fun `parseImage falls back to hiRes JSON when data-old-hires is absent`() {
        setup()
        val html = """<img id="landingImage" src="x"/><script>"hiRes":"https://m.media-amazon.com/images/I/ABC._AC_SL1000_.jpg"</script>"""
        assertThat(extractor.parseImage(html)).isEqualTo("https://m.media-amazon.com/images/I/ABC._AC_SL1000_.jpg")
    }
}
