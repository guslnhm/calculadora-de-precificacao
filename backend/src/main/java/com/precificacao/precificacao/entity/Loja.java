package com.precificacao.precificacao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lojas")
@Getter
@Setter
public class Loja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "cliente_nome", length=150)
    private String clienteNome;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "percentual_imposto", precision = 5, scale = 2)
    private BigDecimal percentualImposto;

    @Column(name = "percentual_custo_fixo", precision = 5, scale = 2)
    private BigDecimal percentualCustoFixo;

    @Column(name = "percentual_taxa_franquia", precision = 5, scale = 2)
    private BigDecimal percentualTaxaFranquia;

    @Column(name = "percentual_taxa_transacao", precision = 5, scale = 2)
    private BigDecimal percentualTaxaTransacao;

    @Column(name = "percentual_taxa_ifood", precision = 5, scale = 2)
    private BigDecimal percentualTaxaIfood;

    @Column(name = "percentual_taxa_repasse", precision = 5, scale = 2)
    private BigDecimal percentualTaxaRepasse;

    @Column(name = "percentual_lucro", precision = 5, scale = 2)
    private BigDecimal percentualLucro;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}