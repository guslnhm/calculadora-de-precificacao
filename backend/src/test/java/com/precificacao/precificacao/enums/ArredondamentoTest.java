package com.precificacao.precificacao.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ArredondamentoTest {

    @ParameterizedTest
    @CsvSource({
            "NENHUM, 19.234, 19.23",
            "NENHUM, 19.235, 19.24",
            "NOVENTA, 19.23, 19.90",
            "NOVENTA, 19.90, 19.90",
            "NOVENTA, 19.95, 20.90",
            "NOVENTA, 0.10, 0.90",
            "NOVENTA_E_NOVE, 19.23, 19.99",
            "NOVENTA_E_NOVE, 19.99, 19.99",
            "NOVENTA_E_NOVE, 20.00, 20.99"
    })
    void arredonda(Arredondamento modo, String valor, String esperado) {
        assertThat(modo.aplicar(new BigDecimal(valor))).isEqualByComparingTo(esperado);
    }
}
