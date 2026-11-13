package com.precificacao.precificacao.monitor;

import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.ifood.merchant.TipoMotivo;
import com.precificacao.precificacao.monitor.MonitorModelos.Evento;
import com.precificacao.precificacao.monitor.MonitorModelos.IncidenteRelatorio;
import com.precificacao.precificacao.monitor.MonitorModelos.MotivoSalvo;
import com.precificacao.precificacao.monitor.MonitorModelos.Pausa;
import com.precificacao.precificacao.monitor.MonitorModelos.TipoEvento;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Textos do WhatsApp: um bloco por loja, separados por uma linha em branco. Ex.:
 * <pre>
 * 🚨 *Burger Moema*
 * 🔴 *FECHADA* desde 17:00
 * Motivo: PDV/gestor de pedidos desconectado
 *
 * ⚠️ *Pizzaria Central*
 * ⏸️ *PAUSADA* desde 17:30
 * Motivo: Falta de motoboy
 * Previsão de volta: 18:00
 *
 * ✅ *Burger Moema*
 * 🟢 *ABERTA* de novo · ficou 32 min fechada
 * </pre>
 */
public final class Mensagens {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DD_MM_HH_MM = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final int MAX_LOJAS = 30;
    /** Espaço entre o bloco de uma loja e o da próxima. */
    public static final String SEPARADOR = "\n\n";

    private Mensagens() {
    }

    public static String duracao(Duration d) {
        long min = Math.max(0, d.toMinutes());
        if (min < 60) {
            return min + " min";
        }
        long h = min / 60, m = min % 60;
        if (h < 24) {
            return h + "h" + String.format("%02d", m);
        }
        return (h / 24) + "d " + (h % 24) + "h" + String.format("%02d", m);
    }

    /** Uma mensagem com um bloco por evento: aberturas, mudanças, lembretes e retornos. */
    public static String mensagemDoGrupo(List<Evento> eventos, ZoneId zona) {
        List<Evento> ordenados = new ArrayList<>(eventos);
        ordenados.sort(Comparator.comparingInt(e -> e.tipo().ordinal()));
        List<String> blocos = new ArrayList<>();
        for (Evento e : ordenados) {
            blocos.add(linha(e, zona));
        }
        if (blocos.size() > MAX_LOJAS) {
            int resto = blocos.size() - MAX_LOJAS;
            blocos = new ArrayList<>(blocos.subList(0, MAX_LOJAS));
            blocos.add("… e mais " + resto + " loja(s).");
        }
        return String.join(SEPARADOR, blocos);
    }

    /** O bloco de uma loja (várias linhas). */
    static String linha(Evento ev, ZoneId zona) {
        StatusOperacao st = ev.status();
        String estado = st != null ? st.estado() : ev.incidente().estado;
        OffsetDateTime inicio = ev.incidente().inicio != null ? ev.incidente().inicio : ev.agora();
        String nome = "*" + ev.nomeLoja() + "*" + sufixoOperacao(ev);
        String desde = "desde " + hora(inicio, zona);
        List<String> l = new ArrayList<>();

        switch (ev.tipo()) {
            case ABERTO -> {
                l.add(("ERROR".equals(estado) || "CLOSED".equals(estado) ? "🚨 " : "⚠️ ") + nome);
                l.add(situacao(estado) + " " + desde);
                detalhes(ev, estado, zona, l);
            }
            case MUDOU -> {
                l.add("🔁 " + nome + " mudou de situação");
                l.add(situacao(estado) + " " + desde);
                detalhes(ev, estado, zona, l);
            }
            case LEMBRETE -> {
                l.add("⏰ " + nome + " continua com problema");
                l.add(situacao(estado) + " há " + duracao(Duration.between(inicio, ev.agora())) + " (" + desde + ")");
                detalhes(ev, estado, zona, l);
            }
            default -> {
                String dur = duracao(Duration.between(inicio, ev.agora()));
                String era = "PAUSED".equals(ev.incidente().estado) ? "pausada" : "fechada";
                if ("OK".equals(ev.estadoFinal()) || "WARNING".equals(ev.estadoFinal())) {
                    l.add("✅ " + nome);
                    l.add("🟢 *ABERTA* de novo · ficou " + dur + " " + era);
                } else {
                    l.add("⚪ " + nome);
                    l.add("Saiu do horário de funcionamento · ficou " + dur + " " + era + " antes");
                }
            }
        }
        return String.join("\n", l);
    }

    /** "⏸️ *PAUSADA*", "🔴 *FECHADA*"... */
    private static String situacao(String estado) {
        return switch (estado == null ? "" : estado) {
            case "PAUSED" -> "⏸️ *PAUSADA*";
            case "WARNING" -> "🟡 *COM RESTRIÇÃO*";
            case "ERROR", "CLOSED" -> "🔴 *FECHADA*";
            default -> "*" + (estado == null ? "?" : estado.toUpperCase()) + "*";
        };
    }

    /** Linhas "Motivo: ..." e, na pausa, "Previsão de volta: 18:00". */
    private static void detalhes(Evento ev, String estado, ZoneId zona, List<String> l) {
        if ("PAUSED".equals(estado)) {
            List<String> partes = new ArrayList<>();
            Pausa p = ev.pausas() == null || ev.pausas().isEmpty() ? null : ev.pausas().get(0);
            if (p != null && p.descricao() != null && !p.descricao().isBlank()) {
                partes.add(p.descricao().trim());
            }
            String resto = motivo(ev, zona, true);
            if (!resto.isEmpty()) {
                partes.add(resto);
            }
            if (!partes.isEmpty()) {
                l.add("Motivo: " + String.join(" + ", partes));
            }
            String volta = fimDaPausa(ev.pausas(), ev.agora(), zona);
            if (!volta.isEmpty()) {
                l.add("Previsão de volta: " + volta);
            }
            return;
        }
        l.add("Motivo: " + motivo(ev, zona, false));
    }

    /** Hora em que a pausa acaba (só se ainda não passou). */
    static String fimDaPausa(List<Pausa> pausas, OffsetDateTime agora, ZoneId zona) {
        if (pausas == null || pausas.isEmpty()) {
            return "";
        }
        LocalDateTime fim = horaLocal(pausas.get(0).fim(), zona);
        LocalDateTime agoraLocal = agora.atZoneSameInstant(zona).toLocalDateTime();
        if (fim == null || !fim.isAfter(agoraLocal)) {
            return "";
        }
        return fim.format(fim.toLocalDate().equals(agoraLocal.toLocalDate()) ? HH_MM : DD_MM_HH_MM);
    }

    private static String motivo(Evento ev, ZoneId zona, boolean semPausa) {
        List<MotivoSalvo> motivos = ev.status() != null ? MonitorModelos.motivosDe(ev.status()) : ev.incidente().motivos;
        Set<TipoMotivo> tipos = new LinkedHashSet<>();
        for (MotivoSalvo m : motivos) {
            TipoMotivo t = m.tipoMotivo();
            if (t != TipoMotivo.FORA_DO_HORARIO) {
                tipos.add(t);
            }
        }
        List<String> partes = new ArrayList<>();
        for (TipoMotivo t : tipos) {
            if (t == TipoMotivo.PAUSA) {
                if (!semPausa) {
                    String p = textoPausa(ev.pausas(), ev.agora(), zona);
                    partes.add(p.isEmpty() ? "pausa" : "pausa " + p);
                }
            } else if (t == TipoMotivo.OUTRO) {
                motivos.stream().filter(m -> m.tipoMotivo() == TipoMotivo.OUTRO).findFirst()
                        .ifPresent(m -> partes.add(m.texto() != null && !m.texto().isBlank() ? m.texto() : m.codigo()));
            } else {
                partes.add(t.getDescricaoCurta());
            }
        }
        if (partes.isEmpty() && !semPausa) {
            String msg = ev.status() != null ? ev.status().mensagem() : ev.incidente().mensagem;
            partes.add(msg == null || msg.isBlank() ? "motivo não informado" : msg);
        }
        String texto = String.join(" + ", partes);
        Boolean reabrivel = ev.status() != null ? ev.status().reabrivel() : ev.incidente().reabrivel;
        if (Boolean.FALSE.equals(reabrivel) && tipos.contains(TipoMotivo.PENALIDADE)) {
            texto += " (não dá para reabrir)";
        }
        return texto;
    }

    /** "\"Falta de motoboy\" até 18:00" a partir da 1ª pausa. */
    static String textoPausa(List<Pausa> pausas, OffsetDateTime agora, ZoneId zona) {
        if (pausas == null || pausas.isEmpty()) {
            return "";
        }
        Pausa p = pausas.get(0);
        List<String> partes = new ArrayList<>();
        if (p.descricao() != null && !p.descricao().isBlank()) {
            partes.add("\"" + p.descricao().trim() + "\"");
        }
        String fim = fimDaPausa(pausas, agora, zona);
        if (!fim.isEmpty()) {
            partes.add("até " + fim);
        }
        return String.join(" ", partes);
    }

    /** O iFood devolve a hora da pausa em hora local (sem fuso) ou, às vezes, com fuso. */
    static LocalDateTime horaLocal(String valor, ZoneId zona) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor).atZoneSameInstant(zona).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(valor);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static String sufixoOperacao(Evento ev) {
        String op = ev.status() != null ? ev.status().operacao() : ev.incidente().operacao;
        if (op == null || op.isBlank() || "?".equals(op) || "delivery".equalsIgnoreCase(op)) {
            return "";
        }
        return " [" + op + "]";
    }

    private static String hora(OffsetDateTime t, ZoneId zona) {
        return t.atZoneSameInstant(zona).format(HH_MM);
    }

    // ------------------------------------------------------------- resumo diário

    public static String resumoDiario(String grupo, List<IncidenteRelatorio> linhas, String dia, int qtdLojas) {
        if (linhas.isEmpty()) {
            return "📊 *Resumo iFood — " + dia + "*\n" + grupo + "\n\nNenhum fechamento inesperado nas "
                    + qtdLojas + " lojas monitoradas. ✅";
        }
        Map<String, Double> porLoja = new LinkedHashMap<>();
        Map<String, Integer> porMotivo = new LinkedHashMap<>();
        for (IncidenteRelatorio r : linhas) {
            porLoja.merge(r.loja(), r.minutos(), Double::sum);
            Set<String> vistos = new LinkedHashSet<>();
            for (MotivoSalvo m : r.motivos()) {
                TipoMotivo t = m.tipoMotivo();
                if (t != TipoMotivo.FORA_DO_HORARIO) {
                    vistos.add(t.getDescricao());
                }
            }
            vistos.forEach(v -> porMotivo.merge(v, 1, Integer::sum));
        }
        double total = porLoja.values().stream().mapToDouble(Double::doubleValue).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Resumo iFood — ").append(dia).append("*\n").append(grupo).append("\n\n")
                .append(linhas.size()).append(" incidente(s) em ").append(porLoja.size()).append(" de ")
                .append(qtdLojas).append(" loja(s) · ").append(duracao(Duration.ofSeconds((long) (total * 60))))
                .append(" fechadas no total\n\n*Lojas mais afetadas:*");
        porLoja.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(10)
                .forEach(e -> sb.append("\n• ").append(e.getKey()).append(": ")
                        .append(duracao(Duration.ofSeconds((long) (e.getValue() * 60)))));
        if (!porMotivo.isEmpty()) {
            sb.append("\n\n*Motivos:*");
            porMotivo.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append("\n• ").append(e.getKey()).append(": ").append(e.getValue()));
        }
        long abertos = linhas.stream().filter(IncidenteRelatorio::emAberto).count();
        if (abertos > 0) {
            sb.append("\n\n⚠️ ").append(abertos).append(" ainda em aberto agora.");
        }
        return sb.toString();
    }
}
