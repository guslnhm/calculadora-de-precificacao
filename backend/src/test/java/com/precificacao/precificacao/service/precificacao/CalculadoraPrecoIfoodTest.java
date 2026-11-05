package com.precificacao.precificacao.service.precificacao;

import com.precificacao.precificacao.dto.SimulacaoRequestDTO;
import com.precificacao.precificacao.dto.SimulacaoReversaRequestDTO;
import com.precificacao.precificacao.entity.Item;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.enums.Arredondamento;
import com.precificacao.precificacao.enums.ModoCupomIfood;
import com.precificacao.precificacao.exception.PercentualInvalidoException;
import com.precificacao.precificacao.repository.ItemRepository;
import com.precificacao.precificacao.service.PrecificacaoService;
import com.precificacao.precificacao.service.precificacao.CalculadoraPrecoIfood.Percentuais;
import com.precificacao.precificacao.service.precificacao.CalculadoraPrecoIfood.Regras;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalculadoraPrecoIfoodTest {

    // imposto 6 + franquia 0 + custo fixo 10 + transação 3,2 + iFood 12 + repasse 1,8 = 33% de custos; lucro 15%
    private static final Percentuais P = new Percentuais(
            bd("6"), bd("0"), bd("10"), bd("3.2"), bd("12"), bd("1.8"), bd("15"));

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void valorBaseSegueAFormulaDaCalculadora() {
        // 10 ÷ (1 − 0,48) = 19,2307… → 19,23
        var r = CalculadoraPrecoIfood.calcular(bd("10.00"), null, P, new Regras(null, null, null));
        assertThat(r.percentualTotal()).isEqualByComparingTo("48");
        assertThat(r.coeficiente()).isEqualByComparingTo("0.52");
        assertThat(r.valorBase()).isEqualByComparingTo("19.23");
        assertThat(r.precoFinal()).isEqualByComparingTo("19.23"); // padrão: BASE, sem arredondar, frete 0
    }

    @Test
    void cupomDaLojaSomaAoValorBase() {
        var r = CalculadoraPrecoIfood.calcular(bd("10.00"), null, P, new Regras(null, ModoCupomIfood.CUPOM_8, null));
        assertThat(r.acrescimoCupom()).isEqualByComparingTo("8");
        assertThat(r.precoFinal()).isEqualByComparingTo("27.23");
    }

    @Test
    void fretePadraoDaLojaEFreteDoItem() {
        var regras = new Regras(bd("2.00"), ModoCupomIfood.BASE, Arredondamento.NENHUM);
        // (10 + 2) ÷ 0,52 = 23,0769… → 23,08
        assertThat(CalculadoraPrecoIfood.calcular(bd("10"), null, P, regras).precoFinal()).isEqualByComparingTo("23.08");
        // frete do item tem prioridade: (10 + 0) ÷ 0,52
        assertThat(CalculadoraPrecoIfood.calcular(bd("10"), bd("0"), P, regras).precoFinal()).isEqualByComparingTo("19.23");
    }

    @Test
    void arredondamentoDepoisDoCupom() {
        var r = CalculadoraPrecoIfood.calcular(bd("10"), null, P,
                new Regras(null, ModoCupomIfood.CUPOM_5, Arredondamento.NOVENTA));
        assertThat(r.precoFinal()).isEqualByComparingTo("24.90"); // 24,23 → 24,90
    }

    @Test
    void percentuaisDe100OuMaisSaoRecusados() {
        var p = new Percentuais(bd("50"), null, bd("30"), null, bd("20"), null, null);
        assertThatThrownBy(() -> CalculadoraPrecoIfood.calcular(bd("10"), null, p, new Regras(null, null, null)))
                .isInstanceOf(PercentualInvalidoException.class);
    }

    @Test
    void margemComoNaSimulacaoReversa() {
        // preço 25, custos 33% = 8,25; lucro = 25 − 8,25 − 10 = 6,75 → 27%
        assertThat(CalculadoraPrecoIfood.margemPercentual(bd("25"), bd("10"), null, P)).isEqualByComparingTo("27.00");
        assertThat(CalculadoraPrecoIfood.margemPercentual(null, bd("10"), null, P)).isNull();
    }

    @Test
    void usaPercentuaisERegrasDaLojaDoItem() {
        Loja loja = lojaExemplo();
        loja.setIfoodModoCupom(ModoCupomIfood.CUPOM_5);
        Item item = new Item();
        item.setLoja(loja);
        item.setCmv(bd("10.00"));
        assertThat(CalculadoraPrecoIfood.calcular(item).precoFinal()).isEqualByComparingTo("24.23");
    }

    @Test
    void lojaNovaComecaSemCupomSemArredondarEFreteZero() {
        Loja loja = new Loja();
        assertThat(loja.getIfoodModoCupom()).isEqualTo(ModoCupomIfood.BASE);
        assertThat(loja.getIfoodArredondamento()).isEqualTo(Arredondamento.NENHUM);
        assertThat(loja.getIfoodFretePadrao()).isEqualByComparingTo("0");
    }

    /** Garante que o cálculo novo dá exatamente o mesmo resultado da tela de simulação. */
    @Test
    void mesmoResultadoDaPrecificacaoService() {
        Loja loja = lojaExemplo();
        Item item = new Item();
        item.setId(1L);
        item.setLoja(loja);
        item.setNomeItem("Açaí 500 ml");
        item.setCmv(bd("9.40"));
        ItemRepository repo = mock(ItemRepository.class);
        when(repo.findById(1L)).thenReturn(Optional.of(item));
        PrecificacaoService service = new PrecificacaoService(repo);

        for (String frete : new String[]{"0", "1.50", "3.99"}) {
            SimulacaoRequestDTO dto = new SimulacaoRequestDTO();
            dto.setItemId(1L);
            dto.setFrete(bd(frete));
            dto.setImposto(loja.getPercentualImposto());
            dto.setTaxaFranquia(loja.getPercentualTaxaFranquia());
            dto.setCustoFixo(loja.getPercentualCustoFixo());
            dto.setTaxaTransacao(loja.getPercentualTaxaTransacao());
            dto.setTaxaIfood(loja.getPercentualTaxaIfood());
            dto.setTaxaRepasse(loja.getPercentualTaxaRepasse());
            dto.setLucro(loja.getPercentualLucro());
            var antigo = service.simular(dto);

            var novo = CalculadoraPrecoIfood.calcular(item.getCmv(), bd(frete), Percentuais.daLoja(loja),
                    new Regras(null, ModoCupomIfood.BASE, Arredondamento.NENHUM));
            assertThat(novo.valorBase()).isEqualByComparingTo(antigo.getValorBase());

            var cupom10 = CalculadoraPrecoIfood.calcular(item.getCmv(), bd(frete), Percentuais.daLoja(loja),
                    new Regras(null, ModoCupomIfood.CUPOM_10, Arredondamento.NENHUM));
            assertThat(cupom10.precoFinal()).isEqualByComparingTo(antigo.getValorCupom10());
        }

        SimulacaoReversaRequestDTO rev = new SimulacaoReversaRequestDTO();
        rev.setItemId(1L);
        rev.setPrecoVenda(bd("22.90"));
        rev.setFrete(bd("0"));
        rev.setImposto(loja.getPercentualImposto());
        rev.setTaxaFranquia(loja.getPercentualTaxaFranquia());
        rev.setCustoFixo(loja.getPercentualCustoFixo());
        rev.setTaxaTransacao(loja.getPercentualTaxaTransacao());
        rev.setTaxaIfood(loja.getPercentualTaxaIfood());
        rev.setTaxaRepasse(loja.getPercentualTaxaRepasse());
        var antigaReversa = service.simularReversa(rev);
        assertThat(CalculadoraPrecoIfood.margemPercentual(bd("22.90"), item.getCmv(), bd("0"), Percentuais.daLoja(loja)))
                .isEqualByComparingTo(antigaReversa.getLucroPercentual());
    }

    private static Loja lojaExemplo() {
        Loja loja = new Loja();
        loja.setNome("Açaí Bom — Moema");
        loja.setPercentualImposto(bd("6"));
        loja.setPercentualTaxaFranquia(bd("0"));
        loja.setPercentualCustoFixo(bd("10"));
        loja.setPercentualTaxaTransacao(bd("3.2"));
        loja.setPercentualTaxaIfood(bd("12"));
        loja.setPercentualTaxaRepasse(bd("1.8"));
        loja.setPercentualLucro(bd("15"));
        return loja;
    }
}
