package com.precificacao.precificacao.enums;

import java.math.BigDecimal;

/**
 * Qual valor da simulação vai para o iFood: o valor base ou o valor com
 * o cupom embutido (os mesmos +R$ 5, +R$ 8 e +R$ 10 da tela de simulação).
 */
public enum ModoCupomIfood {
    BASE(BigDecimal.ZERO),
    CUPOM_5(new BigDecimal("5.00")),
    CUPOM_8(new BigDecimal("8.00")),
    CUPOM_10(new BigDecimal("10.00"));

    private final BigDecimal acrescimo;

    ModoCupomIfood(BigDecimal acrescimo) {
        this.acrescimo = acrescimo;
    }

    public BigDecimal getAcrescimo() {
        return acrescimo;
    }
}
