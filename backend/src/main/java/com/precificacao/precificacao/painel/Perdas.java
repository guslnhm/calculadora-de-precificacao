package com.precificacao.precificacao.painel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Estimativa de vendas perdidas enquanto a loja ficou fechada/pausada.
 *
 * Para cada hora (cheia) em que o incidente esteve aberto, soma a venda média daquela loja naquele dia da
 * semana e hora (curva das últimas semanas), proporcional aos minutos fechados. Hora em que a loja
 * normalmente não vende (fora do horário) não conta.
 */
public final class Perdas {

    /** Chave da curva: dia da semana (1=seg..7=dom) e hora (0–23). */
    public record ChaveHora(int diaSemana, int hora) {
    }

    /** Resultado: minutos fechados em horário de venda e valor estimado. */
    public record Estimativa(long minutos, BigDecimal valor) {
        public static final Estimativa ZERO = new Estimativa(0, BigDecimal.ZERO);

        public Estimativa somar(Estimativa o) {
            return new Estimativa(minutos + o.minutos, valor.add(o.valor));
        }
    }

    private Perdas() {
    }

    public static Estimativa estimar(Map<ChaveHora, BigDecimal> curva, OffsetDateTime inicio, OffsetDateTime fim,
                                     ZoneId zona) {
        if (curva == null || curva.isEmpty() || inicio == null || fim == null || !fim.isAfter(inicio)) {
            return Estimativa.ZERO;
        }
        ZonedDateTime t = inicio.atZoneSameInstant(zona);
        ZonedDateTime limite = fim.atZoneSameInstant(zona);
        long minutos = 0;
        BigDecimal valor = BigDecimal.ZERO;
        while (t.isBefore(limite)) {
            ZonedDateTime proximaHora = t.truncatedTo(ChronoUnit.HOURS).plusHours(1);
            ZonedDateTime ate = proximaHora.isBefore(limite) ? proximaHora : limite;
            long min = Duration.between(t, ate).toMinutes();
            BigDecimal mediaHora = curva.get(new ChaveHora(t.getDayOfWeek().getValue(), t.getHour()));
            if (mediaHora != null && mediaHora.signum() > 0 && min > 0) {
                minutos += min;
                valor = valor.add(mediaHora.multiply(BigDecimal.valueOf(min)).divide(BigDecimal.valueOf(60), 4,
                        RoundingMode.HALF_UP));
            }
            t = ate;
        }
        return new Estimativa(minutos, valor.setScale(2, RoundingMode.HALF_UP));
    }
}
