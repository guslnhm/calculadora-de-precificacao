package com.precificacao.precificacao.cardapio;

import com.precificacao.precificacao.cardapio.CardapioRepository.Pausa;
import com.precificacao.precificacao.cardapio.MensagensItens.Mudancas;
import com.precificacao.precificacao.cardapio.MensagensItens.Retorno;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MensagensItensTest {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    static final OffsetDateTime AGORA = OffsetDateTime.parse("2026-11-12T19:22:00-03:00");

    static Pausa item(String nome, OffsetDateTime inicio) {
        return new Pausa(1, "m", "c", "ITEM", nome, "DEFAULT", nome, "Pães", "PAUSADO", null, inicio, null, false);
    }

    @Test
    void lojasSeparadasPorLinhaEmBrancoEItensEmLista() {
        List<Pausa> pausados = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            pausados.add(item("Pão " + i, AGORA.minusMinutes(5)));
        }
        Pausa baguete = item("Baguette Tradition", AGORA.minusHours(2).minusMinutes(24));
        String texto = MensagensItens.mensagem(List.of(
                new Mudancas("a", "La Boulangerie 306 sul", pausados, List.of()),
                new Mudancas("b", "La Boulangerie Sudoeste", List.of(), List.of(new Retorno(baguete, AGORA)))
        ), true, SP, AGORA);

        String[] blocos = texto.split("\n\n");
        assertThat(blocos).hasSize(2);
        assertThat(blocos[0]).startsWith("⚠️ *La Boulangerie 306 sul*\n⏸️ *12 itens pausados*:\n• Pão 1\n")
                .endsWith("• Pão 10\n• … e mais 2");
        assertThat(blocos[1]).isEqualTo(
                "✅ *La Boulangerie Sudoeste*\n▶️ *Item voltou:* Baguette Tradition (ficou 2h24 pausado)");
    }
}
