package com.precificacao.precificacao.service.precificacao;

import com.precificacao.precificacao.entity.Item;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.enums.Arredondamento;
import com.precificacao.precificacao.enums.ModoCupomIfood;
import com.precificacao.precificacao.exception.PercentualInvalidoException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Cálculo de preço do iFood usado pelo reajuste em massa e pela grade de cardápio.
 *
 * Mesma fórmula da tela de simulação (PrecificacaoService.simular):
 *   valor base = (CMV + frete) ÷ (1 − soma dos %)
 * e, a partir dele, o preço que vai para o iFood:
 *   preço = arredondamento(valor base + acréscimo do cupom da loja)
 *
 * Classe sem dependências do Spring nem do banco, para ser testada isoladamente.
 */
public final class CalculadoraPrecoIfood {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private CalculadoraPrecoIfood() {
    }

    /** Percentuais do iFood de uma loja, em pontos percentuais (6 = 6%). */
    public record Percentuais(
            BigDecimal imposto,
            BigDecimal taxaFranquia,
            BigDecimal custoFixo,
            BigDecimal taxaTransacao,
            BigDecimal taxaIfood,
            BigDecimal taxaRepasse,
            BigDecimal lucro
    ) {
        public Percentuais {
            imposto = zeroSeNulo(imposto);
            taxaFranquia = zeroSeNulo(taxaFranquia);
            custoFixo = zeroSeNulo(custoFixo);
            taxaTransacao = zeroSeNulo(taxaTransacao);
            taxaIfood = zeroSeNulo(taxaIfood);
            taxaRepasse = zeroSeNulo(taxaRepasse);
            lucro = zeroSeNulo(lucro);
        }

        public static Percentuais daLoja(Loja loja) {
            return new Percentuais(
                    loja.getPercentualImposto(),
                    loja.getPercentualTaxaFranquia(),
                    loja.getPercentualCustoFixo(),
                    loja.getPercentualTaxaTransacao(),
                    loja.getPercentualTaxaIfood(),
                    loja.getPercentualTaxaRepasse(),
                    loja.getPercentualLucro()
            );
        }

        /** Soma de todos os percentuais de custo, sem o lucro. */
        public BigDecimal somaCustos() {
            return imposto.add(taxaFranquia).add(custoFixo).add(taxaTransacao).add(taxaIfood).add(taxaRepasse);
        }

        /** Soma de todos os percentuais, com o lucro. */
        public BigDecimal somaTotal() {
            return somaCustos().add(lucro);
        }
    }

    /** Regras da loja que definem o preço final: frete padrão, cupom e arredondamento. */
    public record Regras(BigDecimal fretePadrao, ModoCupomIfood modoCupom, Arredondamento arredondamento) {
        public Regras {
            fretePadrao = zeroSeNulo(fretePadrao);
            modoCupom = modoCupom == null ? ModoCupomIfood.BASE : modoCupom;
            arredondamento = arredondamento == null ? Arredondamento.NENHUM : arredondamento;
        }

        public static Regras daLoja(Loja loja) {
            return new Regras(loja.getIfoodFretePadrao(), loja.getIfoodModoCupom(), loja.getIfoodArredondamento());
        }
    }

    public record Resultado(
            BigDecimal cmv,
            BigDecimal frete,
            BigDecimal percentualTotal,
            BigDecimal coeficiente,
            BigDecimal valorBase,
            BigDecimal acrescimoCupom,
            BigDecimal precoFinal
    ) {
    }

    /**
     * Preço sugerido para o iFood.
     *
     * @param freteDoItem frete específico do item; null usa o frete padrão da loja
     * @throws PercentualInvalidoException se a soma dos percentuais for 100% ou mais
     */
    public static Resultado calcular(BigDecimal cmv, BigDecimal freteDoItem, Percentuais p, Regras regras) {
        Objects.requireNonNull(cmv, "cmv");
        Objects.requireNonNull(p, "percentuais");
        Objects.requireNonNull(regras, "regras");

        BigDecimal frete = freteDoItem != null ? freteDoItem : regras.fretePadrao();
        BigDecimal percentualTotal = p.somaTotal();
        if (percentualTotal.compareTo(CEM) >= 0) {
            throw new PercentualInvalidoException();
        }

        // Mesmas escalas da PrecificacaoService.simular, para dar o mesmo valor base.
        BigDecimal percentualDecimal = percentualTotal.divide(CEM, 4, RoundingMode.HALF_UP);
        BigDecimal coeficiente = BigDecimal.ONE.subtract(percentualDecimal);
        BigDecimal valorBase = cmv.add(frete).divide(coeficiente, 2, RoundingMode.HALF_UP);

        BigDecimal acrescimo = regras.modoCupom().getAcrescimo();
        BigDecimal precoFinal = regras.arredondamento().aplicar(valorBase.add(acrescimo));

        return new Resultado(cmv, frete, percentualTotal, coeficiente, valorBase, acrescimo, precoFinal);
    }

    /** Atalho: calcula com os percentuais e as regras da própria loja do item. */
    public static Resultado calcular(Item item) {
        Loja loja = item.getLoja();
        return calcular(item.getCmv(), item.getFrete(), Percentuais.daLoja(loja), Regras.daLoja(loja));
    }

    /**
     * Margem de lucro (%) de um preço de venda, como na simulação reversa:
     * lucro = preço − preço × (soma dos custos %) − (CMV + frete).
     * Retorna null se o preço for zero ou nulo.
     */
    public static BigDecimal margemPercentual(BigDecimal precoVenda, BigDecimal cmv, BigDecimal frete, Percentuais p) {
        if (precoVenda == null || precoVenda.signum() <= 0) {
            return null;
        }
        BigDecimal custos = p.somaCustos().divide(CEM, 4, RoundingMode.HALF_UP);
        BigDecimal custoPercentualReais = precoVenda.multiply(custos).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lucro = precoVenda
                .subtract(custoPercentualReais)
                .subtract(cmv.add(zeroSeNulo(frete)))
                .setScale(2, RoundingMode.HALF_UP);
        return lucro.divide(precoVenda, 4, RoundingMode.HALF_UP)
                .multiply(CEM)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroSeNulo(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
