@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.dto.MlBuyBoxWinner
import com.whatsappgroups.application.dto.MlItemDetails
import com.whatsappgroups.application.dto.MlProductDetails
import com.whatsappgroups.application.usecase.message.GenerateMessageFromLinkUseCase
import com.whatsappgroups.application.usecase.ml.MercadoLivreAccountUseCase
import com.whatsappgroups.infrastructure.ai.GeminiClient
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
class GenerateMessageFromLinkUseCaseTest {

    @Mock private lateinit var mlApiClient: MercadoLivreApiClient
    @Mock private lateinit var geminiClient: GeminiClient
    @Mock private lateinit var mlAccountUseCase: MercadoLivreAccountUseCase

    private lateinit var useCase: GenerateMessageFromLinkUseCase
    private val userId = UUID.randomUUID()

    private fun setup() {
        useCase = GenerateMessageFromLinkUseCase(mlApiClient, geminiClient, mlAccountUseCase)
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
        whenever(mlApiClient.extractTitleFromPage(any())).thenReturn("Produto Sem Preço")

        val res = useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/p/MLB123"))

        assertThat(res.content).startsWith("Texto gerado 🔥")
        assertThat(res.content).contains("https://www.mercadolivre.com.br/p/MLB123")
        // Prompt has the title but no "Preço" line
        verify(geminiClient).generateText(argThat { contains("Produto Sem Preço") && !contains("Preço:") })
    }

    @Test
    fun `throws when no product info can be resolved`() {
        setup()
        whenever(mlApiClient.getItem(any(), anyOrNull())).thenReturn(null)
        whenever(mlApiClient.getCatalogProduct(any(), any())).thenReturn(null)
        whenever(mlApiClient.extractTitleFromPage(any())).thenReturn(null)

        assertThatThrownBy {
            useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/p/MLB123"))
        }.isInstanceOf(IllegalStateException::class.java)
    }
}
