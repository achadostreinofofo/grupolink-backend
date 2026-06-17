@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.MlBuyBoxWinner
import com.whatsappgroups.application.dto.MlItemDetails
import com.whatsappgroups.application.dto.MlPageProductData
import com.whatsappgroups.application.dto.MlProductDetails
import com.whatsappgroups.application.usecase.message.extractor.MercadoLivreProductExtractor
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
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
class MercadoLivreProductExtractorTest {

    @Mock private lateinit var mlApiClient: MercadoLivreApiClient
    @Mock private lateinit var mlAccountUseCase: MercadoLivreAccountUseCase

    private lateinit var extractor: MercadoLivreProductExtractor
    private val userId = UUID.randomUUID()

    private fun setup() {
        extractor = MercadoLivreProductExtractor(mlApiClient, mlAccountUseCase)
        whenever(mlAccountUseCase.getValidAccessToken(userId)).thenReturn("token")
    }

    private fun item(title: String, price: Double?, original: Double? = null) = MlItemDetails(
        id = "MLB1", title = title, permalink = null, thumbnail = null,
        price = price, originalPrice = original, currencyId = "BRL", condition = "new", availableQuantity = 1
    )

    @Test
    fun `supports Mercado Livre URLs and rejects others`() {
        setup()
        assertThat(extractor.supports("https://produto.mercadolivre.com.br/MLB-1")).isTrue()
        assertThat(extractor.supports("https://meli.la/abc")).isTrue()
        assertThat(extractor.supports("https://www.mercadolibre.com/x")).isTrue()
        assertThat(extractor.supports("https://amazon.com.br/x")).isFalse()
    }

    @Test
    fun `requires a connected ML account`() {
        setup()
        whenever(mlAccountUseCase.getValidAccessToken(userId)).thenReturn(null)

        assertThatThrownBy { extractor.extract("https://www.mercadolivre.com.br/p/MLB123", userId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Conecte sua conta")
    }

    @Test
    fun `throws when the link is not a specific product`() {
        setup()
        whenever(mlApiClient.extractMlbIdFromPageHtml(any())).thenReturn(null)

        assertThatThrownBy { extractor.extract("https://www.mercadolivre.com.br/ofertas", userId) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `extracts from an individual listing via items API`() {
        setup()
        whenever(mlApiClient.getItem("MLB123", "token")).thenReturn(item("Camiseta", 49.9))

        val p = extractor.extract("https://produto.mercadolivre.com.br/MLB-123-camiseta", userId)!!

        assertThat(p.title).isEqualTo("Camiseta")
        assertThat(p.price).isEqualTo(49.9)
        verify(mlApiClient, never()).getCatalogProduct(any(), any())
    }

    @Test
    fun `falls back to catalog product when items returns 403 (null)`() {
        setup()
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)
        whenever(mlApiClient.getCatalogProduct("MLB123", "token"))
            .thenReturn(MlProductDetails(id = "MLB123", name = "Creatina 300g", buyBoxWinner = MlBuyBoxWinner(price = 51.47, originalPrice = null)))

        val p = extractor.extract("https://www.mercadolivre.com.br/creatina/p/MLB123", userId)!!

        assertThat(p.title).isEqualTo("Creatina 300g")
        assertThat(p.price).isEqualTo(51.47)
    }

    @Test
    fun `extracts title, price and image from social page HTML when APIs fail`() {
        setup()
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)
        whenever(mlApiClient.getCatalogProduct(any(), any())).thenReturn(null)
        whenever(mlApiClient.extractMlbIdFromPageHtml(any())).thenReturn("MLB66637233")
        whenever(mlApiClient.extractProductDataFromPage(any())).thenReturn(
            MlPageProductData(
                title = "Creatina Monohidratada 500g",
                price = 69.9,
                originalPrice = 104.9,
                imageUrl = "https://http2.mlstatic.com/img.webp"
            )
        )

        val p = extractor.extract("https://www.mercadolivre.com.br/social/perfil", userId)!!

        assertThat(p.title).isEqualTo("Creatina Monohidratada 500g")
        assertThat(p.price).isEqualTo(69.9)
        assertThat(p.originalPrice).isEqualTo(104.9)
        assertThat(p.imageUrl).isEqualTo("https://http2.mlstatic.com/img.webp")
    }

    @Test
    fun `returns null when no product info can be resolved`() {
        setup()
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)
        whenever(mlApiClient.getCatalogProduct(any(), any())).thenReturn(null)
        whenever(mlApiClient.extractProductDataFromPage(any())).thenReturn(null)

        assertThat(extractor.extract("https://www.mercadolivre.com.br/p/MLB123", userId)).isNull()
    }
}
