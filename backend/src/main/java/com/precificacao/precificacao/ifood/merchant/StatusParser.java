package com.precificacao.precificacao.ifood.merchant;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Interpreta a resposta de GET /merchants/{id}/status. */
public final class StatusParser {

    private StatusParser() {
    }

    public static List<StatusOperacao> ler(JsonNode resposta) {
        List<StatusOperacao> out = new ArrayList<>();
        if (resposta == null || resposta.isNull() || resposta.isMissingNode()) {
            return out;
        }
        if (resposta.isArray()) {
            resposta.forEach(n -> { if (n.isObject()) out.add(lerUma(n)); });
        } else if (resposta.isObject()) {
            out.add(lerUma(resposta));
        }
        return out;
    }

    static StatusOperacao lerUma(JsonNode item) {
        List<StatusOperacao.Motivo> motivos = new ArrayList<>();
        for (JsonNode v : item.path("validations")) {
            String estadoV = v.path("state").asText("").toUpperCase(Locale.ROOT);
            if (!estadoV.isEmpty() && !"OK".equals(estadoV)) {
                String codigo = v.path("code").asText("?");
                motivos.add(new StatusOperacao.Motivo(codigo, estadoV, textoDe(v.path("message")), TipoMotivo.deCodigo(codigo)));
            }
        }

        String estadoIfood = item.path("state").asText("UNKNOWN").toUpperCase(Locale.ROOT);
        boolean pausa = motivos.stream().anyMatch(m -> m.tipo() == TipoMotivo.PAUSA);
        boolean horario = motivos.stream().anyMatch(m -> m.tipo() == TipoMotivo.FORA_DO_HORARIO);
        // Pausa deixa a loja CLOSED no iFood; só é "fechada como esperado" se o motivo for o horário.
        String estado = "CLOSED".equals(estadoIfood) && pausa && !horario ? "PAUSED" : estadoIfood;

        JsonNode disp = item.path("available");
        return new StatusOperacao(
                texto(item.path("operation"), "?"),
                texto(item.path("salesChannel"), "-"),
                estado,
                estadoIfood,
                disp.isBoolean() ? disp.asBoolean() : null,
                List.copyOf(motivos),
                textoDe(item.path("message")),
                reabrivel(item.path("reopenable")),
                item
        );
    }

    /** "title — subtitle — description", sem repetir partes iguais. */
    static String textoDe(JsonNode msg) {
        if (msg == null || msg.isNull() || msg.isMissingNode()) {
            return "";
        }
        if (msg.isTextual()) {
            return msg.asText();
        }
        Set<String> partes = new LinkedHashSet<>();
        for (String campo : new String[]{"title", "subtitle", "description"}) {
            String p = msg.path(campo).asText("");
            if (!p.isBlank()) {
                partes.add(p.trim());
            }
        }
        return String.join(" — ", partes);
    }

    private static Boolean reabrivel(JsonNode r) {
        if (r.isObject()) {
            r = r.path("reopenable");
        }
        return r.isBoolean() ? r.asBoolean() : null;
    }

    private static String texto(JsonNode n, String padrao) {
        String s = n.asText("");
        return s.isBlank() ? padrao : s;
    }
}
