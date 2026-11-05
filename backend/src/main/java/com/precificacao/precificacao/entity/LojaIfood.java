package com.precificacao.precificacao.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Vínculo entre uma loja da calculadora e a loja correspondente no iFood. */
@Entity
@Table(name = "loja_ifood")
@Getter
@Setter
public class LojaIfood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loja_id", nullable = false, unique = true)
    private Loja loja;

    /** ID da loja no iFood (merchantId). */
    @Column(name = "merchant_id", nullable = false, unique = true, length = 64)
    private String merchantId;

    /** Nome da loja como aparece no iFood, guardado no momento do vínculo. */
    @Column(name = "nome_ifood", length = 200)
    private String nomeIfood;

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
    }

    @PreUpdate
    public void preUpdate() {
        atualizadoEm = LocalDateTime.now();
    }
}
