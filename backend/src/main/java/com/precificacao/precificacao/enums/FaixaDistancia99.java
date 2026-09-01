package com.precificacao.precificacao.enums;

import java.math.BigDecimal;

public enum FaixaDistancia99 {

    ATE_3_KM(new BigDecimal("4.00")),
    ATE_5_KM(new BigDecimal("6.00")),
    ACIMA_5_KM(new BigDecimal("7.50"));

    private final BigDecimal custoLogistico;

    FaixaDistancia99(BigDecimal custoLogistico) {
        this.custoLogistico = custoLogistico;
    }

    public BigDecimal getCustoLogistico() {
        return custoLogistico;
    }
}