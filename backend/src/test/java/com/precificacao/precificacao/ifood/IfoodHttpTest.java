package com.precificacao.precificacao.ifood;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IfoodHttpTest {

    private final FakeIfood ifood = new FakeIfood();
    private final List<Long> esperas = new ArrayList<>();
    private final IfoodHttp http = ifood.http(esperas);

    @AfterEach
    void fechar() {
        ifood.close();
    }

    @Test
    void tokenFicaEmCacheEVaiNoCabecalho() {
        ifood.quando("GET", "/merchant/v1.0/merchants/a/status", 200, "[]");
        http.get("/merchant/v1.0/merchants/a/status");
        http.get("/merchant/v1.0/merchants/a/status");
        assertThat(ifood.tokensEmitidos()).isEqualTo(1);
        assertThat(ifood.requisicoes).allSatisfy(r -> assertThat(r.autorizacao()).isEqualTo("Bearer tok1"));
    }

    @Test
    void renovaOTokenUmaVezQuandoORecebe401() {
        ifood.quando("GET", "/x", 401, "{\"message\":\"expired\"}")
                .quando("GET", "/x", 200, "{\"ok\":true}");
        assertThat(http.get("/x").path("ok").asBoolean()).isTrue();
        assertThat(ifood.tokensEmitidos()).isEqualTo(2);
        assertThat(ifood.requisicoes.get(1).autorizacao()).isEqualTo("Bearer tok2");
    }

    @Test
    void esperaERepeteEm429E503() {
        ifood.quando("GET", "/x", 429, "{}", Map.of("Retry-After", "2"))
                .quando("GET", "/x", 503, "{}")
                .quando("GET", "/x", 200, "[1,2]");
        assertThat(http.get("/x").size()).isEqualTo(2);
        assertThat(esperas).hasSize(2);
        assertThat(esperas.get(0)).isEqualTo(2000L);            // respeitou o Retry-After
        assertThat(esperas.get(1)).isBetween(2000L, 2500L);     // backoff da 2ª tentativa
    }

    @Test
    void desisteDepoisDoLimiteDeTentativas() {
        ifood.quando("GET", "/x", 503, "{\"message\":\"fora do ar\"}");
        assertThatThrownBy(() -> http.get("/x"))
                .isInstanceOf(IfoodException.class)
                .satisfies(e -> assertThat(((IfoodException) e).getStatus()).isEqualTo(503));
        assertThat(ifood.chamadas("GET", "/x")).hasSize(5); // 1 + 4 tentativas
    }

    @Test
    void erro403TrazOCorpoEIndicaSemPermissao() {
        ifood.quando("GET", "/x", 403, "{\"message\":\"Forbidden\"}");
        assertThatThrownBy(() -> http.get("/x"))
                .isInstanceOfSatisfying(IfoodException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(403);
                    assertThat(e.semPermissao()).isTrue();
                    assertThat(e.getCorpo()).contains("Forbidden");
                });
        assertThat(ifood.chamadas("GET", "/x")).hasSize(1); // 403 não é repetido
    }

    @Test
    void textoPuroECorpoVazio() {
        ifood.quando("GET", "/versao", 200, "v2")
                .quando("PATCH", "/vazio", 200, "");
        JsonNode v = http.get("/versao");
        assertThat(v.isTextual()).isTrue();
        assertThat(v.asText()).isEqualTo("v2");
        assertThat(http.patch("/vazio", Map.of("a", 1)).isNull()).isTrue();
        assertThat(ifood.chamadas("PATCH", "/vazio").get(0).corpo()).isEqualTo("{\"a\":1}");
    }

    @Test
    void queryStringECodificacao() {
        ifood.quando("GET", "/q", 200, "[]");
        http.get("/q", new java.util.LinkedHashMap<>(Map.of("page", 2)));
        assertThat(ifood.chamadas("GET", "/q").get(0).query()).isEqualTo("page=2");
    }

    @Test
    void semCredenciaisNaoChamaOIfood() {
        IfoodHttp semCredencial = new IfoodHttp(
                new IfoodProperties("", "", ifood.url(), "America/Sao_Paulo", 5, 4), new ObjectMapper());
        assertThatThrownBy(() -> semCredencial.get("/x"))
                .isInstanceOf(IfoodException.class)
                .hasMessageContaining("IFOOD_CLIENT_ID");
        assertThat(ifood.tokensEmitidos()).isZero();
    }
}
