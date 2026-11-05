package com.precificacao.precificacao.ifood.merchant;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Situação de uma operação (ex.: delivery) num canal (ex.: ifood-app) de uma loja,
 * lida de GET /merchants/{id}/status.
 *
 * @param estado      estado já interpretado: OK, WARNING, CLOSED, ERROR ou PAUSED
 *                    (o iFood marca a loja pausada como CLOSED; aqui ela vira PAUSED
 *                    quando o motivo é uma pausa e não o horário de funcionamento)
 * @param estadoIfood estado exatamente como o iFood devolveu
 * @param reabrivel   se a própria loja pode desfazer a pausa (null = o iFood não informou)
 */
public record StatusOperacao(
        String operacao,
        String canal,
        String estado,
        String estadoIfood,
        Boolean disponivel,
        List<Motivo> motivos,
        String mensagem,
        Boolean reabrivel,
        JsonNode bruto
) {

    /** Um motivo (validação) que não está OK. */
    public record Motivo(String codigo, String estado, String texto, TipoMotivo tipo) {
    }

    public boolean temMotivo(TipoMotivo tipo) {
        return motivos.stream().anyMatch(m -> m.tipo() == tipo);
    }

    /** Muda quando o estado ou o conjunto de motivos muda (para gravar só alterações). */
    public String assinatura() {
        return estado + "|" + motivos.stream()
                .map(m -> m.codigo() + ":" + m.estado())
                .sorted()
                .collect(Collectors.joining(","));
    }
}
