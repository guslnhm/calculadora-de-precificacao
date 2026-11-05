package com.precificacao.precificacao.ifood.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.ifood.FakeIfood;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IfoodMerchantClientTest {

    private static final String LOJA = "f8883444-2024-4e1d-a48c-1b3f49212ff0";
    private static final String M = "/merchant/v1.0/merchants";
    private final ObjectMapper mapper = new ObjectMapper();
    private final FakeIfood ifood = new FakeIfood();
    // 29/10/2026 19:38:18 UTC = 16:38:18 em São Paulo
    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-29T19:38:18Z"), ZoneOffset.UTC);
    private final IfoodMerchantClient client =
            new IfoodMerchantClient(ifood.http(new ArrayList<>()), ifood.propriedades(), relogio);

    @AfterEach
    void fechar() {
        ifood.close();
    }

    @Test
    void listaLojasPaginando() {
        String cheia = IntStream.range(0, 100).mapToObj(i -> "{\"id\":\"l" + i + "\"}").collect(Collectors.joining(",", "[", "]"));
        ifood.quando("GET", M, 200, cheia).quando("GET", M, 200, "[{\"id\":\"ultima\",\"name\":\"Loja X\"}]");
        List<JsonNode> lojas = client.listarLojas();
        assertThat(lojas).hasSize(101);
        assertThat(lojas.get(100).path("name").asText()).isEqualTo("Loja X");
        assertThat(ifood.chamadas("GET", M)).extracting(FakeIfood.Requisicao::query)
                .containsExactly("page=1&size=100", "page=2&size=100");
    }

    @Test
    void lojaDeTesteDesconectada() {
        ifood.quandoArquivo("GET", M + "/" + LOJA + "/status", "status_desconectada.json");
        StatusOperacao s = client.status(LOJA).get(0);
        assertThat(s.operacao()).isEqualTo("delivery");
        assertThat(s.canal()).isEqualTo("ifood-app");
        assertThat(s.estado()).isEqualTo("ERROR");
        assertThat(s.disponivel()).isFalse();
        assertThat(s.motivos()).singleElement().satisfies(m -> {
            assertThat(m.codigo()).isEqualTo("is.not.connected.config");
            assertThat(m.tipo()).isEqualTo(TipoMotivo.DESCONECTADA);
        });
        assertThat(s.mensagem()).isEqualTo("Loja fechada — Gestor de Pedidos ou PDV desconectado");
        assertThat(s.reabrivel()).isFalse();
    }

    @Test
    void pausaApareceComoClosedEViraPaused() {
        ifood.quandoArquivo("GET", M + "/" + LOJA + "/status", "status_pausada.json");
        StatusOperacao s = client.status(LOJA).get(0);
        assertThat(s.estadoIfood()).isEqualTo("CLOSED");
        assertThat(s.estado()).isEqualTo("PAUSED");
        assertThat(s.temMotivo(TipoMotivo.PAUSA)).isTrue();
        assertThat(s.temMotivo(TipoMotivo.DESCONECTADA)).isTrue();
        assertThat(s.reabrivel()).isTrue();
        assertThat(s.mensagem()).contains("Em Pausa programada");
    }

    @Test
    void fechadaPeloHorarioContinuaClosed() throws Exception {
        JsonNode r = mapper.readTree("""
                [{"operation":"delivery","salesChannel":"ifood-app","state":"CLOSED",
                  "validations":[{"code":"is.outside.opening.hours","state":"CLOSED"},
                                 {"code":"has.unavailabilities.active","state":"CLOSED"}]}]""");
        StatusOperacao s = StatusParser.ler(r).get(0);
        assertThat(s.estado()).isEqualTo("CLOSED");
        assertThat(s.temMotivo(TipoMotivo.FORA_DO_HORARIO)).isTrue();
        assertThat(s.assinatura()).isEqualTo("CLOSED|has.unavailabilities.active:CLOSED,is.outside.opening.hours:CLOSED");
    }

    @Test
    void codigosDaDocumentacaoTambemSaoReconhecidos() {
        assertThat(TipoMotivo.deCodigo("unavailabilities")).isEqualTo(TipoMotivo.PAUSA);
        assertThat(TipoMotivo.deCodigo("is-connected")).isEqualTo(TipoMotivo.DESCONECTADA);
        assertThat(TipoMotivo.deCodigo("opening-hours")).isEqualTo(TipoMotivo.FORA_DO_HORARIO);
        assertThat(TipoMotivo.deCodigo("radius-restriction")).isEqualTo(TipoMotivo.RAIO_REDUZIDO);
        assertThat(TipoMotivo.deCodigo("payout-blocked")).isEqualTo(TipoMotivo.FINANCEIRO);
        assertThat(TipoMotivo.deCodigo("logistics-blocked")).isEqualTo(TipoMotivo.LOGISTICA);
        assertThat(TipoMotivo.deCodigo("terms-service-violation")).isEqualTo(TipoMotivo.PENALIDADE);
        assertThat(TipoMotivo.deCodigo("status-availability")).isEqualTo(TipoMotivo.LOJA_INDISPONIVEL);
        assertThat(TipoMotivo.deCodigo("algo.novo")).isEqualTo(TipoMotivo.OUTRO);
    }

    @Test
    void pausaVaiComHoraLocalSemFuso() throws Exception {
        ifood.quandoArquivo("POST", M + "/" + LOJA + "/interruptions", "pausa_criada.json");
        JsonNode r = client.pausarPor(LOJA, 10, "Teste de homologação");
        assertThat(r.path("start").asText()).isEqualTo("2026-10-29T16:38:23");

        JsonNode enviado = mapper.readTree(ifood.chamadas("POST", M + "/" + LOJA + "/interruptions").get(0).corpo());
        assertThat(enviado.path("start").asText()).isEqualTo("2026-10-29T16:38:23"); // 16:38:18 + 5 s, em São Paulo
        assertThat(enviado.path("end").asText()).isEqualTo("2026-10-29T16:48:23");
        assertThat(enviado.path("description").asText()).isEqualTo("Teste de homologação");
        assertThat(enviado.path("id").asText()).hasSize(36);
    }

    @Test
    void criarERemoverPausa() {
        ifood.quando("POST", M + "/" + LOJA + "/interruptions", 201, "{\"id\":\"p1\"}")
                .quando("DELETE", M + "/" + LOJA + "/interruptions/p1", 204, "");
        client.criarPausa(LOJA, "x", LocalDateTime.of(2026, 11, 5, 10, 0), LocalDateTime.of(2026, 11, 5, 10, 30));
        client.removerPausa(LOJA, "p1");
        assertThat(ifood.chamadas("DELETE", M + "/" + LOJA + "/interruptions/p1")).hasSize(1);
    }

    @Test
    void horariosDoCenarioDeHomologacao() throws Exception {
        ifood.quando("PUT", M + "/" + LOJA + "/opening-hours", 201, "");
        List<Turno> turnos = new ArrayList<>(Turno.doDia("Sábado", "10:00-19:00"));
        turnos.addAll(Turno.doDia("domingo", "09:00-12:00,13:00-16:00,17:00-23:00"));
        client.definirHorarios(LOJA, turnos);

        JsonNode enviado = mapper.readTree(ifood.chamadas("PUT", M + "/" + LOJA + "/opening-hours").get(0).corpo());
        assertThat(enviado.path("storeId").asText()).isEqualTo(LOJA);
        assertThat(enviado.path("shifts").toString()).isEqualTo(
                "[{\"dayOfWeek\":\"SATURDAY\",\"start\":\"10:00:00\",\"duration\":540},"
                        + "{\"dayOfWeek\":\"SUNDAY\",\"start\":\"09:00:00\",\"duration\":180},"
                        + "{\"dayOfWeek\":\"SUNDAY\",\"start\":\"13:00:00\",\"duration\":180},"
                        + "{\"dayOfWeek\":\"SUNDAY\",\"start\":\"17:00:00\",\"duration\":360}]");
    }

    @Test
    void turnoAteMeiaNoiteEValidacoes() {
        Turno t = Turno.doDia("SEXTA", "18:00-00:00").get(0);
        assertThat(t.duration()).isEqualTo(359);
        assertThat(t.fim().toString()).isEqualTo("23:59");
        assertThatThrownBy(() -> Turno.doDia("Feriado", "10:00-12:00")).hasMessageContaining("Dia inválido");
        assertThatThrownBy(() -> Turno.doDia("Segunda", "12:00-10:00")).hasMessageContaining("fim antes do início");
    }
}
