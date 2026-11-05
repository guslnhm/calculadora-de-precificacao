package com.precificacao.precificacao.ifood.merchant;

import java.util.List;
import java.util.Locale;

/**
 * Motivo de uma loja estar fechada ou com restrição.
 *
 * O iFood devolve códigos diferentes dos documentados (documentação: "unavailabilities";
 * API real: "has.unavailabilities.active"; documentação: "is-connected"; real:
 * "is.not.connected.config"). Por isso a classificação é por trechos do código,
 * na ordem abaixo: a primeira regra que casar vale.
 */
public enum TipoMotivo {
    PAUSA("Pausa/interrupção ativa", "pausa", List.of("unavailab", "interrupt", "pause")),
    DESCONECTADA("Gestor de Pedidos/PDV desconectado (sem receber pedidos)", "PDV/gestor de pedidos desconectado", List.of("connect")),
    FORA_DO_HORARIO("Fora do horário de funcionamento", "fora do horário", List.of("opening", "hour", "shift")),
    RAIO_REDUZIDO("Raio de entrega reduzido (falta de entregadores)", "raio de entrega reduzido", List.of("radius")),
    FINANCEIRO("Pendência financeira", "pendência financeira", List.of("payout", "financ")),
    LOGISTICA("Bloqueio logístico na região", "bloqueio logístico", List.of("logistic")),
    PENALIDADE("Penalidade por violação dos Termos de Serviço", "penalidade do iFood", List.of("terms", "violation", "penalt")),
    LOJA_INDISPONIVEL("Loja desativada ou em fase de teste", "loja desativada/em teste", List.of("availability", "test", "disabled", "inactive")),
    OUTRO("Outro motivo", "outro motivo", List.of());

    private final String descricao;
    private final String descricaoCurta;
    private final List<String> trechos;

    TipoMotivo(String descricao, String descricaoCurta, List<String> trechos) {
        this.descricao = descricao;
        this.descricaoCurta = descricaoCurta;
        this.trechos = trechos;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Texto usado nas mensagens de uma linha do WhatsApp. */
    public String getDescricaoCurta() {
        return descricaoCurta;
    }

    public static TipoMotivo deCodigo(String codigo) {
        String c = codigo == null ? "" : codigo.toLowerCase(Locale.ROOT);
        for (TipoMotivo t : values()) {
            for (String trecho : t.trechos) {
                if (c.contains(trecho)) {
                    return t;
                }
            }
        }
        return OUTRO;
    }
}
