package com.precificacao.precificacao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "itens")
@Getter
@Setter
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id", nullable = false)
    private Loja loja;

    @Column(name = "nome_item", nullable = false, length = 150)
    private String nomeItem;

    @Column(name = "cmv", nullable = false, precision = 12, scale = 2)
    private BigDecimal cmv;

    @Column(name = "preco_venda_inicial", precision = 12, scale = 2)
    private BigDecimal precoVendaInicial;

    @Column(name = "rendimento", precision = 12, scale = 2)
    private BigDecimal rendimento;

    @Column(name = "observacao")
    private String observacao;

    @Column(name = "preco_venda_atual", precision = 12, scale = 2)
    private BigDecimal precoVendaAtual;

    @Column(name = "data_precificacao")
    private LocalDateTime dataPrecificacao;

    @Column(name = "ativo", nullable = false)
    private Boolean ativo;

    /*@ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por")
    private Usuario criadoPor;*/

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
