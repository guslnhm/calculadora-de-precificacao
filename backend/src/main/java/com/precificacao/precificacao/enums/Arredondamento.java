package com.precificacao.precificacao.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Como arredondar o preço final. O padrão é NENHUM: centavos exatos,
 * como a calculadora sempre fez.
 */
public enum Arredondamento {

    /** Mantém o valor calculado, com 2 casas (HALF_UP). */
    NENHUM,

    /** Sobe para o próximo valor terminado em ,90 (19,23 → 19,90; 19,95 → 20,90). */
    NOVENTA,

    /** Sobe para o próximo valor terminado em ,99 (19,23 → 19,99). */
    NOVENTA_E_NOVE;

    public BigDecimal aplicar(BigDecimal valor) {
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
        return switch (this) {
            case NENHUM -> v;
            case NOVENTA -> terminarEm(v, new BigDecimal("0.90"));
            case NOVENTA_E_NOVE -> terminarEm(v, new BigDecimal("0.99"));
        };
    }

    /** Menor valor >= v que termina com os centavos informados. */
    private static BigDecimal terminarEm(BigDecimal v, BigDecimal centavos) {
        BigDecimal inteiro = v.subtract(centavos).setScale(0, RoundingMode.CEILING);
        if (inteiro.signum() < 0) {
            inteiro = BigDecimal.ZERO;
        }
        return inteiro.add(centavos).setScale(2, RoundingMode.UNNECESSARY);
    }
}
