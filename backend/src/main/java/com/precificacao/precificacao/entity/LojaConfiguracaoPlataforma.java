package com.precificacao.precificacao.entity;

import com.precificacao.precificacao.enums.Plataforma;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "loja_configuracao_plataforma",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_loja_plataforma",
                        columnNames = {"loja_id", "plataforma"}
                )
        }
)
@Getter
@Setter
public class LojaConfiguracaoPlataforma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Enumerated(EnumType.STRING)
    @Column(name = "plataforma", nullable = false, length = 30)
    private Plataforma plataforma;

    @Column(
            name = "percentual_imposto",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal percentualImposto = BigDecimal.ZERO;

    @Column(
            name = "percentual_custo_fixo",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal percentualCustoFixo = BigDecimal.ZERO;

    @Column(
            name = "percentual_taxa_plataforma",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal percentualTaxaPlataforma = BigDecimal.ZERO;

    @Column(
            name = "percentual_taxa_franquia",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal percentualTaxaFranquia = BigDecimal.ZERO;

    @Column(
            name = "percentual_taxa_transacao",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal percentualTaxaTransacao = BigDecimal.ZERO;

    @Column(
            name = "percentual_taxa_antecipacao",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal percentualTaxaAntecipacao = BigDecimal.ZERO;

    @Column(
            name = "percentual_lucro",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal percentualLucro = BigDecimal.ZERO;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    public void prePersist() {
        LocalDateTime agora = LocalDateTime.now();

        criadoEm = agora;
        atualizadoEm = agora;

        if (ativo == null) {
            ativo = true;
        }

        if (percentualImposto == null) {
            percentualImposto = BigDecimal.ZERO;
        }

        if (percentualCustoFixo == null) {
            percentualCustoFixo = BigDecimal.ZERO;
        }

        if (percentualTaxaPlataforma == null) {
            percentualTaxaPlataforma = BigDecimal.ZERO;
        }

        if (percentualTaxaFranquia == null) {
            percentualTaxaFranquia = BigDecimal.ZERO;
        }

        if (percentualTaxaTransacao == null) {
            percentualTaxaTransacao = BigDecimal.ZERO;
        }

        if (percentualTaxaAntecipacao == null) {
            percentualTaxaAntecipacao = BigDecimal.ZERO;
        }

        if (percentualLucro == null) {
            percentualLucro = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}