package com.whatsappgroups.infrastructure.amazon

import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.time.Duration

// Baixa o HTML de uma página de produto da Amazon (seguindo o redirect de links curtos amzn.to).
// A Amazon não expõe API pública de leitura de produto sem ser afiliado aprovado, então a
// extração é feita do HTML — com User-Agent de browser para reduzir bloqueios.
@Component
class AmazonScraperClient {

    private val log = LoggerFactory.getLogger(javaClass)

    // Páginas de produto da Amazon são grandes (~1.7 MB), por isso o buffer é bem acima do
    // default de 256 KB do WebClient. followRedirect resolve o amzn.to até a página final.
    private val httpClient = WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(
            HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(15))
        ))
        .codecs { it.defaultCodecs().maxInMemorySize(12 * 1024 * 1024) }
        .defaultHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .defaultHeader("Accept-Language", "pt-BR,pt;q=0.9")
        .build()

    fun fetchProductHtml(url: String): String? {
        return try {
            httpClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToMono<String>()
                .block()
        } catch (e: Exception) {
            log.warn("Falha ao baixar página da Amazon ($url): ${e.message}")
            null
        }
    }
}
