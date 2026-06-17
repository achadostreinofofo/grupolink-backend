@file:Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
package com.whatsappgroups.usecase

import com.whatsappgroups.application.dto.GenerateMessageRequest
import com.whatsappgroups.application.usecase.message.GenerateMessageFromLinkUseCase
import com.whatsappgroups.application.usecase.message.extractor.ExtractedProduct
import com.whatsappgroups.application.usecase.message.extractor.ProductExtractor
import com.whatsappgroups.infrastructure.ai.GeminiClient
import com.whatsappgroups.infrastructure.storage.ProductImageService
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

    @Mock private lateinit var extractor: ProductExtractor
    @Mock private lateinit var geminiClient: GeminiClient
    @Mock private lateinit var productImageService: ProductImageService

    private lateinit var useCase: GenerateMessageFromLinkUseCase
    private val userId = UUID.randomUUID()

    private fun setup() {
        useCase = GenerateMessageFromLinkUseCase(listOf(extractor), geminiClient, productImageService)
        whenever(extractor.supports(any())).thenReturn(true)
        whenever(geminiClient.generateText(any())).thenReturn("Texto gerado 🔥")
    }

    @Test
    fun `rejects URLs not supported by any extractor`() {
        setup()
        whenever(extractor.supports(any())).thenReturn(false)

        assertThatThrownBy { useCase.generate(userId, GenerateMessageRequest("https://amazon.com/x")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `builds prompt with discount and appends the affiliate link`() {
        setup()
        whenever(extractor.extract(any(), eq(userId)))
            .thenReturn(ExtractedProduct(title = "Camiseta", price = 49.9, originalPrice = 99.9, imageUrl = null))

        val res = useCase.generate(userId, GenerateMessageRequest("https://produto.mercadolivre.com.br/MLB-123-camiseta"))

        assertThat(res.content).startsWith("Texto gerado 🔥")
        assertThat(res.content).contains("https://produto.mercadolivre.com.br/MLB-123-camiseta")
        verify(geminiClient).generateText(argThat { contains("Camiseta") && contains("49,90") && contains("99,90") })
    }

    @Test
    fun `ignores original price when it is not greater than the current price`() {
        setup()
        whenever(extractor.extract(any(), eq(userId)))
            .thenReturn(ExtractedProduct(title = "Camiseta", price = 49.9, originalPrice = 49.9, imageUrl = null))

        useCase.generate(userId, GenerateMessageRequest("https://produto.mercadolivre.com.br/MLB-123"))

        // No discount line, just "Preço:"
        verify(geminiClient).generateText(argThat { contains("Preço:") && !contains("OFF") })
    }

    @Test
    fun `generates without price line when price is absent`() {
        setup()
        whenever(extractor.extract(any(), eq(userId)))
            .thenReturn(ExtractedProduct(title = "Produto Sem Preço", price = null, originalPrice = null, imageUrl = null))

        val res = useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/p/MLB123"))

        assertThat(res.content).startsWith("Texto gerado 🔥")
        verify(geminiClient).generateText(argThat { contains("Produto Sem Preço") && !contains("Preço:") })
    }

    @Test
    fun `persists product image to S3 and returns the S3 URL`() {
        setup()
        whenever(extractor.extract(any(), eq(userId)))
            .thenReturn(ExtractedProduct(title = "Creatina", price = 69.9, originalPrice = 104.9, imageUrl = "https://http2.mlstatic.com/img.webp"))
        whenever(productImageService.persist("https://http2.mlstatic.com/img.webp", userId))
            .thenReturn("https://bucket.s3.amazonaws.com/messages/uid/abc.webp")

        val res = useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/social/perfil"))

        assertThat(res.imageUrl).isEqualTo("https://bucket.s3.amazonaws.com/messages/uid/abc.webp")
    }

    @Test
    fun `falls back to the original image URL when S3 persistence fails`() {
        setup()
        whenever(extractor.extract(any(), eq(userId)))
            .thenReturn(ExtractedProduct(title = "Camiseta", price = 49.9, originalPrice = null, imageUrl = "https://http2.mlstatic.com/thumb.webp"))
        whenever(productImageService.persist(any(), any())).thenReturn(null)

        val res = useCase.generate(userId, GenerateMessageRequest("https://produto.mercadolivre.com.br/MLB-123"))

        assertThat(res.imageUrl).isEqualTo("https://http2.mlstatic.com/thumb.webp")
    }

    @Test
    fun `does not touch the image service when there is no image`() {
        setup()
        whenever(extractor.extract(any(), eq(userId)))
            .thenReturn(ExtractedProduct(title = "Camiseta", price = 49.9, originalPrice = null, imageUrl = null))

        val res = useCase.generate(userId, GenerateMessageRequest("https://produto.mercadolivre.com.br/MLB-123"))

        assertThat(res.imageUrl).isNull()
        verify(productImageService, never()).persist(any(), any())
    }

    @Test
    fun `throws when extractor cannot resolve product info`() {
        setup()
        whenever(extractor.extract(any(), eq(userId))).thenReturn(null)

        assertThatThrownBy { useCase.generate(userId, GenerateMessageRequest("https://www.mercadolivre.com.br/p/MLB123")) }
            .isInstanceOf(IllegalStateException::class.java)
    }
}
