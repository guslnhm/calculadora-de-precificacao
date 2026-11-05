package com.precificacao.precificacao.ifood.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.precificacao.precificacao.ifood.FakeIfood;
import com.precificacao.precificacao.ifood.IfoodException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IfoodCatalogClientTest {

    private static final String LOJA = "f8883444-2024-4e1d-a48c-1b3f49212ff0";
    private static final String C = "/catalog/v2.0/merchants/" + LOJA;
    private static final String CATALOGO = "2d961820-e633-4134-b5fd-bb8b0c430c09";
    private static final String CATEGORIA = "76c2af1b-6fe4-48ba-befe-17f36ff03663";
    private static final String ITEM = "df8597e7-ebfd-4949-b305-e5f65c5cd9d7";

    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeIfood ifood = new FakeIfood();
    private final IfoodCatalogClient client = new IfoodCatalogClient(ifood.http(new ArrayList<>()));

    @AfterEach
    void fechar() {
        ifood.close();
    }

    @Test
    void versaoVemComoTextoPuro() {
        ifood.quando("GET", C + "/catalog/version", 200, "v2");
        assertThat(client.versao(LOJA)).isEqualTo("v2");
    }

    @Test
    void catalogosDaLojaDeTeste() {
        ifood.quandoArquivo("GET", C + "/catalogs", "catalogos.json");
        List<JsonNode> cats = client.catalogos(LOJA);
        assertThat(cats).singleElement().satisfies(c -> {
            assertThat(c.path("catalogId").asText()).isEqualTo(CATALOGO);
            assertThat(c.path("context").get(0).asText()).isEqualTo("DEFAULT");
        });
    }

    @Test
    void categoriaSemItensEmbutidosBuscaOsItensEPegaONomeDoProduto() {
        ifood.quandoArquivo("GET", C + "/catalogs/" + CATALOGO + "/categories", "categorias_sem_itens.json")
                .quandoArquivo("GET", C + "/categories/" + CATEGORIA + "/items", "itens_da_categoria.json");

        List<JsonNode> categorias = client.categorias(LOJA, CATALOGO, true);
        assertThat(ifood.chamadas("GET", C + "/catalogs/" + CATALOGO + "/categories").get(0).query())
                .isEqualTo("include_items=true");

        List<ObjectNode> itens = client.itensDe(LOJA, categorias.get(0));
        assertThat(itens).singleElement().satisfies(i -> {
            assertThat(i.path("id").asText()).isEqualTo(ITEM);
            assertThat(i.path("name").asText()).isEqualTo("Açaí 300ml");       // veio do product
            assertThat(i.path("price").path("value").decimalValue()).isEqualByComparingTo("15.9");
            assertThat(i.path("status").asText()).isEqualTo("AVAILABLE");
        });
    }

    @Test
    void precoSempreLevaOOriginalValue() throws Exception {
        ifood.quando("PATCH", C + "/items/price", 200, "");
        client.alterarPrecoItem(LOJA, ITEM, new BigDecimal("16.90"), null);
        JsonNode corpo = mapper.readTree(ifood.chamadas("PATCH", C + "/items/price").get(0).corpo());
        assertThat(corpo.path("itemId").asText()).isEqualTo(ITEM);
        assertThat(corpo.path("price").path("value").decimalValue()).isEqualByComparingTo("16.90");
        assertThat(corpo.path("price").path("originalValue").decimalValue()).isEqualByComparingTo("16.90");
    }

    @Test
    void statusDeItemEComplemento() throws Exception {
        ifood.quando("PATCH", C + "/items/status", 200, "")
                .quando("PATCH", C + "/options/status", 200, "")
                .quando("PATCH", C + "/options/price", 200, "");
        client.alterarStatusItem(LOJA, ITEM, "UNAVAILABLE");
        client.alterarStatusComplemento(LOJA, "opt1", "UNAVAILABLE");
        client.alterarPrecoComplemento(LOJA, "opt1", new BigDecimal("3.50"), new BigDecimal("4.00"));
        assertThat(mapper.readTree(ifood.chamadas("PATCH", C + "/items/status").get(0).corpo()).path("status").asText())
                .isEqualTo("UNAVAILABLE");
        JsonNode p = mapper.readTree(ifood.chamadas("PATCH", C + "/options/price").get(0).corpo());
        assertThat(p.path("optionId").asText()).isEqualTo("opt1");
        assertThat(p.path("price").path("originalValue").decimalValue()).isEqualByComparingTo("4.00");
    }

    @Test
    void criarCategoriaESalvarItem() throws Exception {
        ifood.quando("POST", C + "/catalogs/" + CATALOGO + "/categories", 201,
                        "{\"id\":\"" + CATEGORIA + "\",\"name\":\"Teste Homologação\"}")
                .quando("PUT", C + "/items", 200, FakeIfood.recurso("itens_da_categoria.json"));
        assertThat(client.criarCategoria(LOJA, CATALOGO, "Teste Homologação").path("id").asText()).isEqualTo(CATEGORIA);
        JsonNode corpoCategoria = mapper.readTree(ifood.chamadas("POST", C + "/catalogs/" + CATALOGO + "/categories").get(0).corpo());
        assertThat(corpoCategoria.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(corpoCategoria.path("template").asText()).isEqualTo("DEFAULT");

        ObjectNode item = mapper.createObjectNode();
        item.putObject("item").put("id", ITEM);
        client.salvarItem(LOJA, item);
        assertThat(ifood.chamadas("PUT", C + "/items").get(0).corpo()).contains(ITEM);
    }

    @Test
    void fotoTentaDataUrlEDepoisBase64Puro() throws Exception {
        ifood.quando("POST", C + "/image/upload", 400, "{\"message\":\"invalid image\"}")
                .quando("POST", C + "/image/upload", 200, "{\"path\":\"202611/foto.jpg\"}");
        String path = client.enviarFoto(LOJA, new byte[]{1, 2, 3}, "image/jpeg");
        assertThat(path).isEqualTo("202611/foto.jpg");
        var envios = ifood.chamadas("POST", C + "/image/upload");
        assertThat(mapper.readTree(envios.get(0).corpo()).path("image").asText()).isEqualTo("data:image/jpeg;base64,AQID");
        assertThat(mapper.readTree(envios.get(1).corpo()).path("image").asText()).isEqualTo("AQID");
    }

    @Test
    void fotoAceitaSoJpgOuPng() {
        assertThatThrownBy(() -> client.enviarFoto(LOJA, new byte[]{1}, "image/gif"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fotoSemCaminhoNaRespostaEhErro() {
        ifood.quando("POST", C + "/image/upload", 200, "{}");
        assertThatThrownBy(() -> client.enviarFoto(LOJA, new byte[]{1}, "image/png"))
                .isInstanceOf(IfoodException.class)
                .hasMessageContaining("caminho da imagem");
    }
}
