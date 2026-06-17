package com.whatsappgroups.infrastructure.amazon

import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.net.URI
import java.time.Duration

// Baixa o HTML de uma página de produto da Amazon (a Amazon não expõe API pública de leitura
// sem afiliado aprovado, então a extração é feita do HTML).
//
// Resolução de redirect MANUAL: links amzn.to redirecionam (301) para a página final. O
// followRedirect automático do Reactor Netty não garante o reenvio dos headers (User-Agent) no
// salto cross-host, e sem cara de browser a Amazon serve página de bloqueio. Por isso seguimos
// os redirects nós mesmos, reaplicando os headers de browser em TODOS os saltos (incl. o GET
// final) — espelhando o `curl -L -A` que funciona.
@Component
class AmazonScraperClient {

    private val log = LoggerFactory.getLogger(javaClass)

    private val userAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // followRedirect(false): controlamos os saltos manualmente para garantir os headers.
    private val client = WebClient.builder()
        .clientConnector(ReactorClientHttpConnector(
            HttpClient.create()
                .followRedirect(false)
                .responseTimeout(Duration.ofSeconds(15))
        ))
        .codecs { it.defaultCodecs().maxInMemorySize(12 * 1024 * 1024) }
        .defaultHeader("User-Agent", userAgent)
        .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .defaultHeader("Accept-Language", "pt-BR,pt;q=0.9")
        .build()

    fun fetchProductHtml(url: String): String? {
        var current = url
        for (hop in 0..5) {
            val entity = try {
                client.get()
                    .uri(URI.create(current))
                    .exchangeToMono { res -> res.toEntity(String::class.java) }
                    .block()
            } catch (e: Exception) {
                log.warn("Falha ao baixar página da Amazon ($current): ${e.message}")
                return null
            } ?: return null

            val status = entity.statusCode
            if (status.is3xxRedirection) {
                val location = entity.headers.location?.toString()
                if (location.isNullOrBlank()) {
                    log.warn("Amazon redirect ${status.value()} sem Location para $current")
                    return null
                }
                current = if (location.startsWith("http")) location
                          else URI.create(current).resolve(location).toString()
                continue
            }

            val body = entity.body
            log.info("Amazon fetch ${status.value()} (${body?.length ?: 0} bytes) para $current")
            if (!status.is2xxSuccessful) return null
            return body
        }
        log.warn("Amazon: excesso de redirects a partir de $url")
        return null
    }
}
