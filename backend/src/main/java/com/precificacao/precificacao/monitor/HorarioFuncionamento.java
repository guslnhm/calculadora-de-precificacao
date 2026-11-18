package com.precificacao.precificacao.monitor;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Horário de funcionamento de uma loja (GET /merchants/{id}/opening-hours): turnos com dia, início e duração.
 * Turno que passa da meia-noite continua valendo no dia seguinte.
 */
public final class HorarioFuncionamento {

    public record Turno(DayOfWeek dia, LocalTime inicio, int minutos) {
    }

    private HorarioFuncionamento() {
    }

    public static List<Turno> ler(JsonNode resp) {
        List<Turno> out = new ArrayList<>();
        if (resp == null) {
            return out;
        }
        JsonNode shifts = resp.has("shifts") ? resp.get("shifts") : resp;
        for (JsonNode s : shifts) {
            try {
                out.add(new Turno(DayOfWeek.valueOf(s.path("dayOfWeek").asText().toUpperCase()),
                        LocalTime.parse(s.path("start").asText("00:00:00")), s.path("duration").asInt()));
            } catch (RuntimeException e) {
                // turno em formato inesperado: ignora
            }
        }
        return out;
    }

    /** A loja deveria estar aberta neste momento? Sem turnos conhecidos, considera aberta (não esconde alerta). */
    public static boolean aberta(List<Turno> turnos, ZonedDateTime quando) {
        if (turnos == null || turnos.isEmpty()) {
            return true;
        }
        LocalDateTime agora = quando.toLocalDateTime();
        for (Turno t : turnos) {
            for (int voltar = 0; voltar <= 1; voltar++) {   // hoje e ontem (turno que atravessa a meia-noite)
                LocalDateTime dia = agora.minusDays(voltar);
                if (dia.getDayOfWeek() != t.dia()) {
                    continue;
                }
                LocalDateTime ini = dia.toLocalDate().atTime(t.inicio());
                LocalDateTime fim = ini.plusMinutes(t.minutos());
                if (!agora.isBefore(ini) && agora.isBefore(fim)) {
                    return true;
                }
            }
        }
        return false;
    }
}
