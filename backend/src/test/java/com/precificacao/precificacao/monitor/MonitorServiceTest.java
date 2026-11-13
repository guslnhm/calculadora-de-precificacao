package com.precificacao.precificacao.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.ifood.merchant.StatusParser;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorModelos.Pausa;
import com.precificacao.precificacao.monitor.MonitorModelos.TipoEvento;
import com.precificacao.precificacao.whatsapp.ConsoleSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Simula um dia de operação contra o Postgres do CI, com um iFood e um WhatsApp falsos. */
@SpringBootTest
class MonitorServiceTest {

    static final String A = "aaaa-1111", B = "bbbb-2222", C = "cccc-3333";

    @Autowired MonitorRepository repo;
    @Autowired GrupoWhatsappRepository grupos;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    Relogio relogio;
    IfoodFalso ifood;
    ConsoleSender wpp;

    @BeforeEach
    void limpar() {
        repo.limparTudo();
        relogio = new Relogio(Instant.parse("2026-10-29T20:00:00Z")); // 17:00 em São Paulo
        ifood = new IfoodFalso();
        wpp = new ConsoleSender();
        // Admin acompanha A (e recebe as lojas sem grupo); Zona Sul acompanha A e B; C fica sem grupo
        grupos.salvar(null, "Admin", "admin-group", false, true, true, true, List.of(A));
        grupos.salvar(null, "Zona Sul", "zs-group", false, false, true, true, List.of(A, B));
    }

    MonitorService monitor(int ciclosConfirmacao, int falhasParaAvisar, boolean enviar) {
        MonitorProperties cfg = new MonitorProperties(true, enviar, 30, ciclosConfirmacao, List.of("ERROR"), true,
                30, true, List.of(), 4, 60, falhasParaAvisar, "08:00");
        return new MonitorService(cfg, ifood, repo, grupos, wpp, relogio,
                new IfoodProperties("", "", "http://ifood.falso", "America/Sao_Paulo", 5, 4));
    }

    MonitorService monitor() {
        return monitor(2, 10, true);
    }

    // ------------------------------------------------------------- cenários

    @Test
    void cicloCompletoDeUmIncidente() {
        MonitorService mon = monitor();
        mon.ciclo();
        assertThat(wpp.para("admin-group")).anyMatch(t -> t.contains("Lojas sem grupo") && t.contains("Poke Sem Grupo"));
        wpp.enviadas.clear();

        // B pausada: o 1º ciclo não alerta (confirmação = 2 ciclos)
        ifood.status.put(B, pausada("Falta de motoboy"));
        relogio.passar(30);
        assertThat(mon.ciclo()).isEmpty();
        relogio.passar(30);
        var eventos = mon.ciclo();
        assertThat(eventos).extracting(e -> e.tipo()).containsExactly(TipoEvento.ABERTO);
        assertThat(wpp.para("zs-group")).containsExactly(
                "⚠️ *Burger Moema*\n⏸️ *PAUSADA* desde 17:00\nMotivo: Falta de motoboy\nPrevisão de volta: 18:00");
        assertThat(wpp.para("admin-group")).isEmpty(); // B não é do admin
        assertThat(repo.incidentesAbertos()).singleElement().satisfies(i -> assertThat(i.merchantId).isEqualTo(B));
        wpp.enviadas.clear();

        // o motivo muda para penalidade: avisa a mudança
        ifood.status.put(B, real("ERROR", "{\"code\":\"terms-service-violation\",\"state\":\"ERROR\"}", false));
        relogio.passar(30);
        assertThat(mon.ciclo()).extracting(e -> e.tipo()).containsExactly(TipoEvento.MUDOU);
        assertThat(wpp.para("zs-group")).containsExactly(
                "🔁 *Burger Moema* mudou de situação\n🔴 *FECHADA* desde 17:00\nMotivo: penalidade do iFood (não dá para reabrir)");
        // mesmo motivo de novo: silêncio
        relogio.passar(30);
        assertThat(mon.ciclo()).isEmpty();
        wpp.enviadas.clear();

        // 30 min depois: lembrete
        relogio.passar(30 * 60);
        assertThat(mon.ciclo()).extracting(e -> e.tipo()).containsExactly(TipoEvento.LEMBRETE);
        assertThat(wpp.para("zs-group").get(0)).isEqualTo("⏰ *Burger Moema* continua com problema\n"
                + "🔴 *FECHADA* há 31 min (desde 17:00)\nMotivo: penalidade do iFood (não dá para reabrir)");
        wpp.enviadas.clear();

        // volta ao normal
        ifood.status.put(B, ok());
        relogio.passar(30);
        assertThat(mon.ciclo()).extracting(e -> e.tipo()).containsExactly(TipoEvento.NORMALIZADO);
        assertThat(wpp.para("zs-group")).containsExactly("✅ *Burger Moema*\n🟢 *ABERTA* de novo · ficou 32 min fechada");
        assertThat(repo.incidentesAbertos()).isEmpty();

        OffsetDateTime agora = OffsetDateTime.now(relogio);
        var rel = repo.relatorio(agora.minusHours(2), agora.plusHours(1), null, agora);
        assertThat(rel).singleElement().satisfies(r -> {
            assertThat(r.loja()).isEqualTo("Burger Moema");
            assertThat(r.estadoFinal()).isEqualTo("OK");
            assertThat(r.minutos()).isBetween(31.5, 33.0);
        });
        // histórico só grava mudanças: OK, PAUSED, ERROR (penalidade), OK
        assertThat(repo.historico(B, agora.minusHours(2), agora.plusHours(1)))
                .extracting(h -> h.get("estado")).containsExactly("OK", "PAUSED", "ERROR", "OK");
    }

    @Test
    void lojaDeTesteRealDesconectadaPausadaEDesconectada() {
        MonitorService mon = monitor(1, 10, true);
        ifood.status.put(B, desconectada());
        mon.ciclo();
        assertThat(wpp.para("zs-group")).containsExactly(
                "🚨 *Burger Moema*\n🔴 *FECHADA* desde 17:00\nMotivo: PDV/gestor de pedidos desconectado");

        ifood.status.put(B, real("CLOSED",
                "{\"code\":\"is.not.connected.config\",\"state\":\"CLOSED\"},{\"code\":\"has.unavailabilities.active\",\"state\":\"CLOSED\"}",
                true));
        ifood.pausas.put(B, List.of(new Pausa("p1", "Teste de homologação", "2026-10-29T17:00:05", "2026-10-29T17:10:05")));
        relogio.passar(30);
        mon.ciclo();
        assertThat(ultima("zs-group")).isEqualTo(
                "🔁 *Burger Moema* mudou de situação\n⏸️ *PAUSADA* desde 17:00\n"
                        + "Motivo: Teste de homologação + PDV/gestor de pedidos desconectado\nPrevisão de volta: 17:10");
        assertThat(repo.incidentesAbertos()).hasSize(1); // o mesmo incidente

        ifood.status.put(B, desconectada());
        relogio.passar(30);
        mon.ciclo();
        assertThat(ultima("zs-group")).isEqualTo(
                "🔁 *Burger Moema* mudou de situação\n🔴 *FECHADA* desde 17:00\nMotivo: PDV/gestor de pedidos desconectado");
    }

    @Test
    void lojasSemGrupoVaoParaOAdminNumaMensagemSo() {
        MonitorService mon = monitor(1, 10, true);
        mon.ciclo();
        wpp.enviadas.clear();
        ifood.status.put(A, pausada(null));
        ifood.status.put(C, desconectada());
        relogio.passar(30);
        mon.ciclo();
        List<String> admin = wpp.para("admin-group");
        assertThat(admin).hasSize(1);
        assertThat(admin.get(0).split("\n\n")).hasSize(2)
                .anyMatch(b -> b.startsWith("⚠️ *Pizzaria Central*\n⏸️ *PAUSADA*"))
                .anyMatch(b -> b.startsWith("🚨 *Poke Sem Grupo*\n🔴 *FECHADA*"));
        assertThat(wpp.para("zs-group")).singleElement().satisfies(t -> {
            assertThat(t).contains("Pizzaria Central");
            assertThat(t).doesNotContain("Poke");
        });
    }

    @Test
    void foraDoHorarioNaoAlertaEEncerraOIncidente() {
        MonitorService mon = monitor(1, 10, true);
        ifood.status.put(A, foraDoHorario());
        mon.ciclo();
        assertThat(wpp.enviadas).noneMatch(e -> e.texto().contains("FECHADA"));
        ifood.status.put(A, desconectada());
        relogio.passar(30);
        mon.ciclo();
        ifood.status.put(A, foraDoHorario());
        relogio.passar(60);
        assertThat(mon.ciclo()).extracting(e -> e.tipo()).containsExactly(TipoEvento.NORMALIZADO);
        assertThat(ultima("zs-group")).isEqualTo("⚪ *Pizzaria Central*\nSaiu do horário de funcionamento · ficou 1 min fechada antes");
    }

    @Test
    void reiniciarNaoDuplicaOIncidente() {
        MonitorService mon = monitor(1, 10, true);
        ifood.status.put(B, desconectada());
        mon.ciclo();
        assertThat(wpp.para("zs-group")).hasSize(1);
        MonitorService reiniciado = monitor(1, 10, true); // "reinicia" com o mesmo banco
        relogio.passar(30);
        assertThat(reiniciado.ciclo()).isEmpty();
        assertThat(repo.incidentesAbertos()).hasSize(1);
        assertThat(wpp.para("zs-group")).hasSize(1);
    }

    @Test
    void falhaDeLeituraDeUmaLojaAvisaOAdminUmaVez() {
        MonitorService mon = monitor(2, 3, true);
        mon.ciclo();
        wpp.enviadas.clear();
        ifood.falhas.put(B, new IfoodException(403, "GET status", "{\"message\":\"Forbidden\"}"));
        for (int i = 0; i < 5; i++) {
            relogio.passar(30);
            mon.ciclo();
        }
        assertThat(wpp.para("admin-group")).singleElement().satisfies(t -> {
            assertThat(t).contains("Burger Moema");
            assertThat(t).contains("revogou a permissão");
        });
        ifood.falhas.remove(B);
        relogio.passar(30);
        mon.ciclo();
        assertThat(ultima("admin-group")).contains("Voltei a ler o status de *Burger Moema*");
    }

    @Test
    void lojaTiradaDasPermissoesSaiDoMonitorSemAviso() {
        MonitorService mon = monitor(2, 3, true);
        mon.ciclo();
        wpp.enviadas.clear();
        ifood.removidas.add(B);
        ifood.falhas.put(B, new IfoodException(403, "GET status", "{\"message\":\"Forbidden\"}"));
        for (int i = 0; i < 12; i++) {
            relogio.passar(30);
            mon.ciclo();
        }
        assertThat(wpp.para("admin-group")).isEmpty();
        assertThat(mon.lojasMonitoradas()).containsExactlyInAnyOrder(A, C);
        assertThat(repo.lojasAtivas()).extracting(LojaMerchant::merchantId).doesNotContain(B);
    }

    @Test
    void falhaGeralGeraUmAvisoSo() {
        MonitorService mon = monitor();
        mon.ciclo();
        wpp.enviadas.clear();
        for (String m : List.of(A, B, C)) {
            ifood.falhas.put(m, new IfoodException(503, "indisponível", null));
        }
        for (int i = 0; i < 12; i++) {
            relogio.passar(30);
            mon.ciclo();
        }
        assertThat(wpp.para("admin-group")).singleElement().satisfies(t -> assertThat(t).contains("NENHUMA loja"));
    }

    @Test
    void modoSombraRegistraMasNaoEnvia() {
        MonitorService mon = monitor(1, 10, false);
        ifood.status.put(B, desconectada());
        mon.ciclo();
        assertThat(wpp.enviadas).isEmpty();
        List<Map<String, Object>> envios = repo.envios(10);
        assertThat(envios).anySatisfy(e -> {
            assertThat(e.get("situacao")).isEqualTo("SOMBRA");
            assertThat(e.get("grupo")).isEqualTo("Zona Sul");
            assertThat((String) e.get("corpo")).startsWith("🚨 *Burger Moema*\n🔴 *FECHADA*");
        });
    }

    @Test
    void resumoDiario() {
        MonitorService mon = monitor(1, 10, true);
        mon.ciclo();
        ifood.status.put(B, pausada("Falta de motoboy"));
        relogio.passar(30);
        mon.ciclo();
        relogio.passar(45 * 60);
        ifood.status.put(B, ok());
        mon.ciclo();
        wpp.enviadas.clear();

        relogio.ajustar(Instant.parse("2026-10-30T11:05:00Z")); // 08:05 em São Paulo do dia seguinte
        assertThat(mon.resumoPendente()).isTrue();
        Map<String, String> enviados = mon.enviarResumo(null);
        assertThat(enviados.get("Zona Sul")).contains("Resumo iFood — 29/10/2026").contains("Burger Moema: 45 min");
        assertThat(enviados.get("Admin")).contains("Nenhum fechamento"); // admin: A e C, sem incidentes
        assertThat(mon.resumoPendente()).isFalse(); // já enviado hoje
    }

    @Test
    void mapaDeGrupos() {
        long id = grupos.salvar(null, "Centro", "c-group", false, false, true, true, List.of(C, C, " "));
        assertThat(grupos.buscar(id)).get().satisfies(g -> assertThat(g.lojas()).containsExactly(C));
        grupos.salvar(id, "Centro", "c-group", false, false, false, true, List.of(A));
        assertThat(grupos.buscar(id)).get().satisfies(g -> {
            assertThat(g.lojas()).containsExactly(A);
            assertThat(g.avisarRetorno()).isFalse();
        });
        assertThat(GrupoWhatsappRepository.gruposDaLoja(grupos.listar(true), "desconhecida"))
                .extracting(MonitorModelos.Grupo::nome).containsExactly("Admin");
    }

    // ------------------------------------------------------------- apoio

    String ultima(String chat) {
        List<String> l = wpp.para(chat);
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    List<StatusOperacao> parse(String json) {
        try {
            return StatusParser.ler(mapper.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    List<StatusOperacao> ok() {
        return parse("[{\"operation\":\"delivery\",\"salesChannel\":\"ifood-app\",\"state\":\"OK\",\"available\":true,"
                + "\"validations\":[{\"code\":\"is.connected.config\",\"state\":\"OK\"}]}]");
    }

    List<StatusOperacao> desconectada() {
        return real("ERROR", "{\"code\":\"is.not.connected.config\",\"state\":\"ERROR\","
                + "\"message\":{\"title\":\"Loja fechada\",\"subtitle\":\"Gestor de Pedidos ou PDV desconectado\"}}", null);
    }

    List<StatusOperacao> foraDoHorario() {
        return real("CLOSED", "{\"code\":\"is.outside.opening.hours\",\"state\":\"CLOSED\"}", null);
    }

    List<StatusOperacao> pausada(String descricao) {
        ifood.pausas.put(B, descricao == null ? List.of()
                : List.of(new Pausa("p1", descricao, "2026-10-29T17:00:00", "2026-10-29T18:00:00")));
        return real("CLOSED", "{\"code\":\"has.unavailabilities.active\",\"state\":\"CLOSED\"}", true);
    }

    List<StatusOperacao> real(String estado, String validacoes, Boolean reabrivel) {
        String reopen = reabrivel == null ? "" : ",\"reopenable\":{\"reopenable\":" + reabrivel + "}";
        return parse("[{\"operation\":\"delivery\",\"salesChannel\":\"ifood-app\",\"state\":\"" + estado + "\","
                + "\"available\":false,\"validations\":[" + validacoes + "]" + reopen + "}]");
    }

    class IfoodFalso implements FonteIfood {
        final Map<String, List<StatusOperacao>> status = new HashMap<>();
        final Map<String, RuntimeException> falhas = new HashMap<>();
        final Map<String, List<Pausa>> pausas = new HashMap<>();
        final java.util.Set<String> removidas = new java.util.HashSet<>();

        IfoodFalso() {
            status.put(A, ok());
            status.put(B, ok());
            status.put(C, ok());
        }

        @Override
        public List<LojaMerchant> listarLojas() {
            return java.util.stream.Stream.of(new LojaMerchant(A, "Pizzaria Central", null),
                    new LojaMerchant(B, "Burger Moema", null), new LojaMerchant(C, "Poke Sem Grupo", null))
                    .filter(l -> !removidas.contains(l.merchantId())).toList();
        }

        @Override
        public List<StatusOperacao> status(String merchantId) {
            RuntimeException f = falhas.get(merchantId);
            if (f != null) {
                throw f;
            }
            return status.get(merchantId);
        }

        @Override
        public List<Pausa> pausas(String merchantId) {
            return pausas.getOrDefault(merchantId, new ArrayList<>());
        }

        @Override
        public void renovarAcesso() {
        }
    }

    static class Relogio extends Clock {
        private Instant agora;

        Relogio(Instant inicio) {
            this.agora = inicio;
        }

        void passar(long segundos) {
            agora = agora.plus(Duration.ofSeconds(segundos));
        }

        void ajustar(Instant i) {
            agora = i;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
