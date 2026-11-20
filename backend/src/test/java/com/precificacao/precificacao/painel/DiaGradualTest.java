package com.precificacao.precificacao.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.cardapio.CardapioTest.Relogio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** O iFood libera o dia anterior aos poucos: loja zerada que costuma vender e leituras que ainda mudam. */
@SpringBootTest
public class DiaGradualTest {

    static final String A = "grad-a";
    static final String B = "grad-b";
    static final LocalDate ONTEM = LocalDate.parse("2026-11-19");

    @Autowired JdbcTemplate jdbc;
    @Autowired PainelRepository repo;
    final ObjectMapper json = new ObjectMapper();

    /** Pedidos que o "iFood" devolve para ontem, por loja. */
    final Map<String, Integer> ifood = new HashMap<>();
    Relogio relogio;
    PainelColetaService coleta;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM painel_dia WHERE merchant_id LIKE 'grad-%'");
        jdbc.update("DELETE FROM painel_resumo_enviado WHERE merchant_id LIKE 'grad-%'");
        jdbc.update("DELETE FROM ifood_merchant WHERE merchant_id LIKE 'grad-%'");
        jdbc.update("INSERT INTO ifood_merchant (merchant_id, nome, ativo) VALUES (?, 'Grad A', TRUE), (?, 'Grad B', TRUE)", A, B);
        // as duas lojas venderam todos os dias da semana anterior
        for (int i = 1; i <= 8; i++) {
            for (String m : List.of(A, B)) {
                LeitorKpis.Dia d = new LeitorKpis.Dia(ONTEM.minusDays(i));
                d.pedidos = 10;
                repo.salvarDias(m, List.of(d), Map.of(), Instant.parse("2026-11-19T12:00:00Z").atOffset(ZoneOffset.UTC));
            }
        }
        relogio = new Relogio(Instant.parse("2026-11-20T12:40:00Z"));
        LeitorKpis.Fonte fonte = (m, corpo) -> resposta(ifood.getOrDefault(m, 0));
        coleta = new PainelColetaService(fonte, repo, new PainelProperties("x", "y", "", true, 15, 180, 8), relogio,
                java.time.ZoneId.of("America/Sao_Paulo"));
    }

    JsonNode resposta(int pedidos) {
        try {
            if (pedidos == 0) {
                return json.readTree("{\"data\":[]}");
            }
            return json.readTree("{\"data\":[{\"groupByKey\":{\"value\":{\"orderStatus\":\"CONCLUDED\"},\"count\":"
                    + pedidos + "},\"gmv\":{\"sum\":" + pedidos * 50 + "}}]}");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void lojaZeradaQueCostumaVenderSegura() {
        ifood.put(A, 47);
        coleta.recoletarDia(ONTEM);
        assertThat(repo.zeradasSuspeitas(ONTEM)).containsExactly(B);

        ifood.put(B, 11);
        coleta.recoletarDia(ONTEM);
        assertThat(repo.zeradasSuspeitas(ONTEM)).isEmpty();
        assertThat(repo.movimentoPorLoja(ONTEM)).containsEntry(A, 47).containsEntry(B, 11);
    }

    @Test
    void estavelSoComDuasLeiturasIguaisSeparadas() {
        ifood.put(A, 20);
        ifood.put(B, 5);
        coleta.recoletarDia(ONTEM);
        assertThat(coleta.estavel(ONTEM)).isFalse(); // uma leitura só

        relogio.passar(Duration.ofMinutes(5));
        coleta.recoletarDia(ONTEM);
        assertThat(coleta.estavel(ONTEM)).isFalse(); // iguais, mas próximas demais

        relogio.passar(Duration.ofMinutes(20));
        ifood.put(B, 11); // o iFood completou a loja B
        coleta.recoletarDia(ONTEM);
        assertThat(coleta.estavel(ONTEM)).isFalse();

        relogio.passar(Duration.ofMinutes(20));
        coleta.recoletarDia(ONTEM);
        assertThat(coleta.estavel(ONTEM)).isTrue();
    }

    @Test
    void lojasQueMudaramDepoisDoEnvio() {
        Map<String, Integer> enviado = Map.of(A, 0, B, 5, "c", 9);
        Map<String, Integer> agora = Map.of(A, 47, B, 11, "c", 9, "d", 3);
        assertThat(ResumoDiarioService.mudaram(enviado, agora)).containsExactly(A, B, "d");
        assertThat(ResumoDiarioService.mudaram(agora, agora)).isEmpty();
    }
}
