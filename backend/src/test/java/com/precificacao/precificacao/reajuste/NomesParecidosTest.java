package com.precificacao.precificacao.reajuste;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NomesParecidosTest {

    @Test
    void normaliza() {
        assertThat(NomesParecidos.normalizar("Coca-Cola  350 ML")).isEqualTo("coca cola 350ml");
        assertThat(NomesParecidos.normalizar("Açaí 1 Litro")).isEqualTo("acai 1l");
        assertThat(NomesParecidos.normalizar("Batata Frita c/ Cheddar & Bacon")).isEqualTo("batata frita cheddar bacon");
        assertThat(NomesParecidos.normalizar("Pizza Grande (8 Fatias)")).isEqualTo("pizza grande 8fatias");
    }

    @Test
    void mesmoItemComNomeEscritoDiferente() {
        assertThat(NomesParecidos.semelhanca("X-Bacon", "X Bacon")).isEqualTo(1.0);
        assertThat(NomesParecidos.semelhanca("Açaí 300ml", "ACAI 300 ML")).isEqualTo(1.0);
        assertThat(NomesParecidos.semelhanca("Batata frita com cheddar", "Batata Frita c/ Cheddar")).isEqualTo(1.0);
        assertThat(NomesParecidos.semelhanca("Cheddar Burger Duplo", "Burger Duplo Cheddar")).isGreaterThan(0.9);
        assertThat(NomesParecidos.semelhanca("X-Salada Especial", "X Salada")).isGreaterThan(0.55);
        assertThat(NomesParecidos.semelhanca("Hamburguer Clasico", "Hambúrguer Clássico")).isGreaterThan(0.55);
    }

    @Test
    void tamanhoDiferenteEOutroProduto() {
        assertThat(NomesParecidos.semelhanca("Coca-Cola 350ml", "Coca-Cola 2L")).isLessThan(0.55);
        assertThat(NomesParecidos.semelhanca("Pizza 4 fatias", "Pizza 8 fatias")).isLessThan(0.55);
        assertThat(NomesParecidos.semelhanca("Pizza Calabresa", "Hambúrguer")).isLessThan(0.3);
        assertThat(NomesParecidos.semelhanca("", "X")).isZero();
    }
}
