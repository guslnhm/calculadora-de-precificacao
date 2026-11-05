package com.precificacao.precificacao.ifood.merchant;

import java.text.Normalizer;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Uma faixa de horário de funcionamento, no formato do iFood:
 * dia da semana (MONDAY…SUNDAY), início (HH:mm:ss) e duração em minutos.
 */
public record Turno(String dayOfWeek, String start, int duration) {

    private static final Map<String, String> DIAS = Map.of(
            "SEGUNDA", "MONDAY", "TERCA", "TUESDAY", "QUARTA", "WEDNESDAY", "QUINTA", "THURSDAY",
            "SEXTA", "FRIDAY", "SABADO", "SATURDAY", "DOMINGO", "SUNDAY");

    public Turno {
        if (duration <= 0 || duration > 1440) {
            throw new IllegalArgumentException("Duração inválida: " + duration + " min");
        }
    }

    /**
     * Cria a partir de um dia em português e faixas "HH:mm-HH:mm", ex.:
     * doDia("Domingo", "09:00-12:00,13:00-16:00"). Fim 00:00 ou 24:00 = até 23:59.
     */
    public static List<Turno> doDia(String dia, String faixas) {
        String chave = Normalizer.normalize(dia, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toUpperCase(Locale.ROOT);
        String dow = DIAS.getOrDefault(chave, DIAS.containsValue(chave) ? chave : null);
        if (dow == null) {
            throw new IllegalArgumentException("Dia inválido: " + dia);
        }
        List<Turno> out = new ArrayList<>();
        for (String faixa : faixas.split(",")) {
            String[] p = faixa.trim().split("-");
            if (p.length != 2) {
                throw new IllegalArgumentException("Faixa inválida: " + faixa + " (use HH:mm-HH:mm)");
            }
            int ini = minutos(p[0]);
            int fim = minutos(p[1]);
            if (fim == 0 || fim == 1440) {
                fim = 1439;
            }
            if (fim <= ini) {
                throw new IllegalArgumentException("Faixa inválida em " + dia + ": " + faixa + " (fim antes do início)");
            }
            out.add(new Turno(dow, String.format("%02d:%02d:00", ini / 60, ini % 60), fim - ini));
        }
        return out;
    }

    public LocalTime inicio() {
        return LocalTime.parse(start.length() == 5 ? start + ":00" : start);
    }

    /** Fim da faixa (23:59 no máximo). */
    public LocalTime fim() {
        int m = Math.min(inicio().getHour() * 60 + inicio().getMinute() + duration, 1439);
        return LocalTime.of(m / 60, m % 60);
    }

    private static int minutos(String hhmm) {
        String s = hhmm.trim();
        if ("24:00".equals(s)) {
            return 1440;
        }
        LocalTime t = LocalTime.parse(s.length() == 4 ? "0" + s : s);
        return t.getHour() * 60 + t.getMinute();
    }
}
