package com.precificacao.precificacao.dto;

import com.precificacao.precificacao.enums.Plataforma;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ConfiguracaoPlataformaRequestDTO {

    @NotNull(message = "A plataforma é obrigatória")
    private Plataforma plataforma;

    @NotNull(message = "O imposto é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal percentualImposto;

    @NotNull(message = "O custo fixo é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal percentualCustoFixo;

    @NotNull(message = "A taxa da plataforma é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal percentualTaxaPlataforma;

    @NotNull(message = "A taxa de franquia é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal percentualTaxaFranquia;

    @NotNull(message = "A taxa de transação é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal percentualTaxaTransacao;

    @NotNull(message = "A taxa de antecipação é obrigatória")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal percentualTaxaAntecipacao;

    @NotNull(message = "O lucro é obrigatório")
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal percentualLucro;
}