package com.whatsappgroups.application.usecase.message.extractor

import java.util.UUID

// Dados de produto normalizados, independentes do marketplace de origem.
// originalPrice é o preço "cheio"/riscado bruto — quem monta o prompt decide se o usa
// (apenas quando há desconto real, isto é, originalPrice > price).
data class ExtractedProduct(
    val title: String,
    val price: Double?,
    val originalPrice: Double?,
    val imageUrl: String?,
    val coupon: String? = null   // ex.: "Cupom 5% OFF" — benefício de cupom, quando disponível
)

// Strategy: isola a obtenção dos dados de produto por marketplace (Mercado Livre, Amazon, ...).
// O use case escolhe o extractor pelo primeiro que `supports(url)` e delega a extração.
interface ProductExtractor {
    fun supports(url: String): Boolean

    // Retorna null quando não foi possível obter os dados mínimos (título) do produto.
    // Pode lançar IllegalArgumentException/IllegalStateException para erros específicos do
    // marketplace (ex.: conta não conectada, link que não aponta para um produto).
    fun extract(url: String, userId: UUID): ExtractedProduct?
}
