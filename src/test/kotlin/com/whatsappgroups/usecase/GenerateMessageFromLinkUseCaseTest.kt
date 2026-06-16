@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.dto.MlBuyBoxWinner
import com.whatsappgroups.application.dto.MlItemDetails
import com.whatsappgroups.application.dto.MlPageProductData
import com.whatsappgroups.application.dto.MlProductDetails
import com.whatsappgroups.application.usecase.message.GenerateMessageFromLinkUseCase
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.infrastructure.ai.GeminiClient
import com.whatsappgroups.infrastructure.ml.MercadoLivreApiClient
import com.whatsappgroups.infrastructure.storage.S3UploadService
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
class GenerateMessageFromLinkUseCaseTest {

    @Mock private lateinit var mlApiClient: MercadoLivreApiClient
    @Mock private lateinit var geminiClient: GeminiClient
    @Mock private lateinit var mlAccountUseCase: MercadoLivreAccountUseCase
    @Mock private lateinit var s3UploadService: S3UploadService

    private lateinit var useCase: GenerateMessageFromLinkUseCase
    private val userId = UUID.randomUUID()

    private fun setup() {
        useCase = GenerateMessageFromLinkUseCase(mlApiClient, geminiClient, mlAccountUseCase, s3UploadService)
        whenever(mlAccountUseCase.getValidAccessToken(userId)).thenReturn("token")
        whenever(geminiClient.generateText(any())).thenReturn("Texto gerado 🔥")
    }

    private fun item(title: String, price: Double?, original: Double? = null) = MlItemDetails(
        id = "MLB1", title = title, permalink = null, thumbnail = null,
        price = price, originalPrice = original, currencyId = "BRL", condition = "new", availableQuantity = 1
    )

    @Test
    fun `rejects non Mercado Livre URLs`() {
        setup()
        assertThatThrownBy { useCase.generate(userId, GenerateMessageRequest("https://amazon.com/x")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `requires a connected ML account`() {
        setup()
        whenever(mlAccountUseCase.getValidAccessToken(userId)).thenReturn(null)
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)

        assertThatThrownBy {
            useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/p/MLB123"))
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Conecte sua conta")
    }

    @Test
    fun `generates from an individual listing via items API`() {
        setup()
        whenever(mlApiClient.getItem("MLB123", "token")).thenReturn(item("Camiseta", 49.9))

        val res = useCase.generate(userId, GenerateMessageRequest("https://produto.mercadolivre.com.br/MLB-123-camiseta"))

        assertThat(res.content).startsWith("Texto gerado 🔥")
        assertThat(res.content).contains("https://produto.mercadolivre.com.br/MLB-123-camiseta")
        verify(mlApiClient, never()).getCatalogProduct(any(), any())
        verify(geminiClient).generateText(argThat { contains("Camiseta") && contains("49,90") })
    }

    @Test
    fun `falls back to catalog product when items returns 403 (null)`() {
        setup()
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)
        whenever(mlApiClient.getCatalogProduct("MLB123", "token"))
            .thenReturn(MlProductDetails(id = "MLB123", name = "Creatina 300g", buyBoxWinner = MlBuyBoxWinner(price = 51.47, originalPrice = null)))

        val res = useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/creatina/p/MLB123"))

        assertThat(res.content).startsWith("Texto gerado 🔥")
        assertThat(res.content).contains("https://www.mercadolivre.com.br/creatina/p/MLB123")
        verify(geminiClient).generateText(argThat { contains("Creatina 300g") && contains("51,47") })
    }

    @Test
    fun `falls back to og title and generates without price`() {
        setup()
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)
        whenever(mlApiClient.getCatalogProduct(any(), any())).thenReturn(null)
        whenever(mlApiClient.extractProductDataFromPage(any()))
            .thenReturn(MlPageProductData(title = "Produto Sem Preço"))

        val res = useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/p/MLB123"))

        assertThat(res.content).startsWith("Texto gerado 🔥")
        assertThat(res.content).contains("https://www.mercadolivre.com.br/p/MLB123")
        // Prompt has the title but no "Preço" line
        verify(geminiClient).generateText(argThat { contains("Produto Sem Preço") && !contains("Preço:") })
    }

    @Test
    fun `extracts social page data and reuploads the image to S3`() {
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
        whenever(mlApiClient.downloadImage("https://http2.mlstatic.com/img.webp"))
            .thenReturn(byteArrayOf(1, 2, 3) to "image/webp")
        whenever(s3UploadService.uploadImageBytes(any(), eq("image/webp"), eq(userId.toString())))
            .thenReturn("https://bucket.s3.amazonaws.com/messages/uid/abc.webp")

        val res = useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/social/perfil"))

        assertThat(res.content).startsWith("Texto gerado 🔥")
        // imageUrl is the S3 URL, not the external ML URL
        assertThat(res.imageUrl).isEqualTo("https://bucket.s3.amazonaws.com/messages/uid/abc.webp")
        verify(geminiClient).generateText(argThat { contains("Creatina Monohidratada 500g") && contains("69,90") && contains("104,90") })
    }

    @Test
    fun `falls back to the original ML image URL when S3 upload fails`() {
        setup()
        whenever(mlApiClient.getItem("MLB123", "token"))
            .thenReturn(MlItemDetails(
                id = "MLB123", title = "Camiseta", permalink = null,
                thumbnail = "https://http2.mlstatic.com/thumb.webp",
                price = 49.9, originalPrice = null, currencyId = "BRL", condition = "new", availableQuantity = 1
            ))
        whenever(mlApiClient.downloadImage(any())).thenReturn(null)

        val res = useCase.generate(userId, GenerateMessageRequest("https://produto.mercadolivre.com.br/MLB-123-camiseta"))

        assertThat(res.imageUrl).isEqualTo("https://http2.mlstatic.com/thumb.webp")
        verify(s3UploadService, never()).uploadImageBytes(any(), any(), any())
    }

    @Test
    fun `throws when no product info can be resolved`() {
        setup()
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)
        whenever(mlApiClient.getCatalogProduct(any(), any())).thenReturn(null)
        whenever(mlApiClient.extractProductDataFromPage(any())).thenReturn(null)

        assertThatThrownBy {
            useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/p/MLB123"))
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
