package com.whatsappgroups.infrastructure.ml

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MercadoLivreApiClientTest {

    private val client = MercadoLivreApiClient(clientId = "test", clientSecret = "test")

    @Test
    fun `parses shared item_id even when the page lists many recommendations`() {
        // Affiliate social page: og:url/canonical point back to the profile, the body has many
        // recommendation MLBs, but the shared product is the "item_id" field.
        val html = """
            <html><head>
            <meta property="og:url" content="https://www.mercadolivre.com.br/social/loja?ref=ENC"/>
            <link rel="canonical" href="https://www.mercadolivre.com.br/social/loja"/>
            </head><body>
            <a href="https://www.mercadolivre.com.br/reco/p/MLB4555189589?reco_item_pos=1">reco</a>
            <a href="https://www.mercadolivre.com.br/reco/p/MLB99999999?reco_item_pos=2">reco</a>
            <script>{"shared":{"item_id":"MLB66637233"},"experiments":{}}</script>
            </body></html>
        """.trimIndent()

        assertThat(client.parseSharedMlbIdFromHtml(html)).isEqualTo("MLB66637233")
    }

    @Test
    fun `falls back to canonical product URL when there is no item_id`() {
        val html = """
            <html><head>
            <link rel="canonical" href="https://www.mercadolivre.com.br/produto/p/MLB123456"/>
            </head><body>conteúdo</body></html>
        """.trimIndent()

        assertThat(client.parseSharedMlbIdFromHtml(html)).isEqualTo("MLB123456")
    }

    @Test
    fun `falls back to first MLB in body as last resort`() {
        val html = "<html><body><div data-id=\"MLB-777888\">x</div></body></html>"
        assertThat(client.parseSharedMlbIdFromHtml(html)).isEqualTo("MLB777888")
    }

    @Test
    fun `returns null when no MLB id is present`() {
        assertThat(client.parseSharedMlbIdFromHtml("<html><body>sem produto</body></html>")).isNull()
    }

    @Test
    fun `parseSharedProductUrlFromHtml returns the full permalink matching the shared item`() {
        val html = """
            <html><body>
            <a href="https://www.mercadolivre.com.br/outro-produto/p/MLB111?reco_item_pos=1">reco</a>
            <script>{"item_id":"MLB20302850"}</script>
            <a href="https://www.mercadolivre.com.br/beta-alanina-250g-growth/p/MLB20302850?matt_event_ts=1&amp;source=x#polycard">compartilhado</a>
            </body></html>
        """.trimIndent()

        assertThat(client.parseSharedProductUrlFromHtml(html))
            .isEqualTo("https://www.mercadolivre.com.br/beta-alanina-250g-growth/p/MLB20302850")
    }

    @Test
    fun `parseSharedProductUrlFromHtml falls back to bare catalog URL when permalink is absent`() {
        val html = """<html><body><script>{"item_id":"MLB777"}</script></body></html>"""
        assertThat(client.parseSharedProductUrlFromHtml(html))
            .isEqualTo("https://www.mercadolivre.com.br/p/MLB777")
    }

    @Test
    fun `parseSharedProductUrlFromHtml returns null when there is no shared item`() {
        assertThat(client.parseSharedProductUrlFromHtml("<html><body>nada</body></html>")).isNull()
    }

    @Test
    fun `parseProductDataFromHtml extracts title, image and shared product price block`() {
        // Mirrors a real /social page: og tags for the shared product, then a components array
        // whose FIRST "type":"price" is the shared product (R$ 69,90, era R$ 104,90), followed by
        // other listed products with different prices that must be ignored.
        val html = """
            <meta property="og:title" content="Creatina Monohidratada 500g Growth Supplements"/>
            <meta property="og:image" content="https://http2.mlstatic.com/D_NQ_NP_602304-MLA.webp"/>
            {"type":"title","title":{"text":"Creatina Monohidratada 500g"}},
            {"type":"price","id":"price","column":1,"price":{"previous_price":{"value":104.9,"currency":"BRL"},"current_price":{"value":69.9,"currency":"BRL"},"discount_label":{"text":"33% OFF"}}},
            {"type":"price","id":"price","column":1,"price":{"previous_price":{"value":34.9},"current_price":{"value":30.9}}}
        """.trimIndent()

        val data = client.parseProductDataFromHtml(html)

        assertThat(data.title).isEqualTo("Creatina Monohidratada 500g Growth Supplements")
        assertThat(data.imageUrl).isEqualTo("https://http2.mlstatic.com/D_NQ_NP_602304-MLA.webp")
        assertThat(data.price).isEqualTo(69.9)
        assertThat(data.originalPrice).isEqualTo(104.9)
    }

    @Test
    fun `parseProductDataFromHtml returns nulls when page has no product data`() {
        val data = client.parseProductDataFromHtml("<html><body>sem produto</body></html>")
        assertThat(data.title).isNull()
        assertThat(data.price).isNull()
        assertThat(data.originalPrice).isNull()
        assertThat(data.imageUrl).isNull()
    }
}
