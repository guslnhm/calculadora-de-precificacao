package com.precificacao.precificacao.lojaifood;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.ifood.merchant.StatusParser;
import com.precificacao.precificacao.ifood.merchant.Turno;
import com.precificacao.precificacao.lojaifood.LojaIfoodService.Faixa;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LojaIfoodServiceTest {

    final ObjectMapper mapper = new ObjectMapper();
    final ApiFalsa api = new ApiFalsa();
    final LojaIfoodService service = new LojaIfoodService(api, 0);

    JsonNode json(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void informacoesDaLoja() {
        var info = service.informacoes("m1");
        assertThat(info.get("nome")).isEqualTo("Teste - ACAI PURISSIMO");
        assertThat(info.get("razaoSocial")).isEqualTo("ACAI PURISSIMO FRANCHISING LTDA");
        @SuppressWarnings("unchecked")
        Map<String, Object> end = (Map<String, Object>) info.get("endereco");
        assertThat(end).containsEntry("cidade", "São Paulo").containsEntry("rua", "Rua Teste");
        assertThat(info.get("operacoes").toString()).contains("DELIVERY").contains("IFOOD");
        assertThat((List<?>) info.get("status")).hasSize(1);
        assertThat(service.lojas()).singleElement().satisfies(l -> assertThat(l.id()).isEqualTo("m1"));
    }

    @Test
    void pausarListarERemover() {
        var r = service.pausar("m1", 30, "Pausa homologação");
        assertThat(api.pausasPedidas).containsExactly("30|Pausa homologação");
        assertThat(service.pausas("m1")).singleElement().satisfies(p -> assertThat(p.descricao()).isEqualTo("Pausa homologação"));
        assertThat(r.get("criada").toString()).contains("p-1");

        assertThat(service.removerPausa("m1", null)).containsExactly("p-1");
        assertThat(api.removidas).containsExactly("p-1");
        assertThatThrownBy(() -> service.pausar("m1", 0, "x")).hasMessageContaining("Duração");
    }

    @Test
    void removerUsaOStatusQuandoAListaAindaNaoMostrouAPausa() {
        api.status = StatusParser.ler(json("""
                [{"operation":"DELIVERY","salesChannel":"IFOOD","state":"CLOSED","available":false,
                  "validations":[{"code":"has.unavailabilities.active","state":"CLOSED"}],
                  "reopenable":{"identifier":"p-status","type":"UNAVAILABILITY","reopenable":true}}]
                """));
        assertThat(service.removerPausa("m1", null)).containsExactly("p-status");
        api.status = List.of();
        assertThatThrownBy(() -> service.removerPausa("m1", null)).hasMessageContaining("Nenhuma pausa");
    }

    @Test
    void horariosDoCenarioDeHomologacao() {
        Map<String, List<Faixa>> dias = new LinkedHashMap<>();
        dias.put("SATURDAY", List.of(new Faixa("10:00", "19:00")));
        dias.put("SUNDAY", List.of(new Faixa("09:00", "12:00"), new Faixa("13:00", "16:00"), new Faixa("17:00", "23:00")));
        dias.put("MONDAY", List.of());
        var resultado = service.definirHorarios("m1", dias);
        assertThat(api.turnos).extracting(Turno::dayOfWeek).containsExactly("SATURDAY", "SUNDAY", "SUNDAY", "SUNDAY");
        assertThat(api.turnos.get(0).start()).isEqualTo("10:00:00");
        assertThat(api.turnos.get(0).duration()).isEqualTo(540);
        assertThat(resultado.get("SUNDAY")).extracting(Faixa::inicio).containsExactly("09:00", "13:00", "17:00");
        assertThat(resultado.get("SUNDAY").get(2).fim()).isEqualTo("23:00");
        assertThat(resultado.get("MONDAY")).isEmpty();
        assertThatThrownBy(() -> service.definirHorarios("m1", Map.of("MONDAY", List.of(new Faixa("12:00", "10:00")))))
                .hasMessageContaining("fim antes");
        assertThatThrownBy(() -> service.definirHorarios("m1", Map.of())).hasMessageContaining("pelo menos um dia");
    }

    class ApiFalsa implements LojaIfoodService.Api {
        final List<String> pausasPedidas = new ArrayList<>();
        final List<String> removidas = new ArrayList<>();
        final List<JsonNode> pausas = new ArrayList<>();
        List<Turno> turnos = List.of();
        List<StatusOperacao> status;

        ApiFalsa() {
            status = null;
        }

        @Override
        public List<JsonNode> lojas() {
            return List.of(json("{\"id\":\"m1\",\"name\":\"Teste - ACAI PURISSIMO\",\"corporateName\":\"ACAI PURISSIMO FRANCHISING LTDA\"}"));
        }

        @Override
        public JsonNode detalhes(String merchantId) {
            return json("""
                    {"id":"m1","name":"Teste - ACAI PURISSIMO","corporateName":"ACAI PURISSIMO FRANCHISING LTDA",
                     "averageTicket":35.5,"merchantType":"RESTAURANT",
                     "address":{"street":"Rua Teste","number":"100","district":"Centro","city":"São Paulo","state":"SP",
                                "postalCode":"01000000","country":"BR"},
                     "operations":[{"name":"DELIVERY","salesChannels":[{"name":"IFOOD","enabled":true}]}]}
                    """);
        }

        @Override
        public List<StatusOperacao> status(String merchantId) {
            if (status != null) {
                return status;
            }
            return StatusParser.ler(json("[{\"operation\":\"DELIVERY\",\"salesChannel\":\"IFOOD\",\"state\":\"OK\",\"available\":true,\"validations\":[]}]"));
        }

        @Override
        public JsonNode pausas(String merchantId) {
            return mapper.valueToTree(pausas);
        }

        @Override
        public JsonNode pausar(String merchantId, int minutos, String descricao) {
            pausasPedidas.add(minutos + "|" + descricao);
            JsonNode p = json("{\"id\":\"p-1\",\"description\":\"" + descricao + "\",\"start\":\"2026-11-06T15:00:05\",\"end\":\"2026-11-06T15:30:05\"}");
            pausas.add(p);
            return p;
        }

        @Override
        public void removerPausa(String merchantId, String pausaId) {
            removidas.add(pausaId);
            pausas.removeIf(p -> p.path("id").asText().equals(pausaId));
        }

        @Override
        public JsonNode horarios(String merchantId) {
            return mapper.valueToTree(Map.of("storeId", merchantId, "shifts", turnos));
        }

        @Override
        public void definirHorarios(String merchantId, List<Turno> t) {
            turnos = t;
        }
    }
}
