package com.whatsappgroups.infrastructure.storage

import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.time.Duration
import java.util.UUID

// Baixa a imagem principal de um produto (CDN de qualquer marketplace) e persiste no nosso S3,
// para que a mídia salva na mensagem seja idêntica a um upload manual — sem depender de URL
// externa no momento do envio. Comum a todos os marketplaces (não acoplado ao Mercado Livre).
@Service
class ProductImageService(
    private val s3UploadService: S3UploadService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // User-Agent de browser para não ser bloqueado por CDNs; buffer ampliado para imagens grandes.
    private val webClient = WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(
            HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(10))
        ))
        .codecs { it.defaultCodecs().maxInMemorySize(8 * 1024 * 1024) }
        .defaultHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        .defaultHeader("Accept", "image/avif,image/webp,image/png,image/*;q=0.8,*/*;q=0.5")
        .build()

    // Retorna a URL do S3, ou null em qualquer falha de download/upload (o caller deve então
    // cair para a URL original — graceful degradation, a mensagem ainda tem imagem).
    fun persist(imageUrl: String, userId: UUID): String? {
        val (bytes, contentType) = download(imageUrl) ?: return null
        return try {
            s3UploadService.uploadImageBytes(bytes, contentType, userId.toString())
        } catch (e: Exception) {
            log.warn("Falha ao subir imagem do produto para o S3 ($imageUrl): ${e.message}")
            null
        }
    }

    private fun download(url: String): Pair<ByteArray, String>? {
        return try {
            val response = webClient.get()
                .uri(URI.create(url))
                .retrieve()
                .toEntity(ByteArray::class.java)
                .block() ?: return null
            val bytes = response.body?.takeIf { it.isNotEmpty() } ?: return null
            val contentType = response.headers.contentType?.toString() ?: "image/jpeg"
            bytes to contentType
        } catch (e: Exception) {
            log.warn("Falha ao baixar imagem $url: ${e.message}")
            null
        }
    }
}
