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
 * Textos do WhatsApp no formato curto: uma linha por loja. Ex.:
 * <pre>
 * 🔴 *Burger Moema* fechou — PDV/gestor de pedidos desconectado · desde 17:00
 * ⏸️ *Pizzaria Central* pausada "Falta de motoboy" até 18:00 · desde 17:30
 * ✅ *Burger Moema* voltou (ficou 32 min fechada)
 * </pre>
 */
public final class Mensagens {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DD_MM_HH_MM = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final int MAX_LINHAS = 30;

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

    /** Uma mensagem com uma linha por evento: aberturas, mudanças, lembretes e retornos. */
    public static String mensagemDoGrupo(List<Evento> eventos, ZoneId zona) {
        List<Evento> ordenados = new ArrayList<>(eventos);
        ordenados.sort(Comparator.comparingInt(e -> e.tipo().ordinal()));
        List<String> linhas = new ArrayList<>();
        for (Evento e : ordenados) {
            linhas.add(linha(e, zona));
        }
        if (linhas.size() > MAX_LINHAS) {
            int resto = linhas.size() - MAX_LINHAS;
            linhas = new ArrayList<>(linhas.subList(0, MAX_LINHAS));
            linhas.add("… e mais " + resto + " loja(s).");
        }
        return String.join("\n", linhas);
    }

    static String linha(Evento ev, ZoneId zona) {
        StatusOperacao st = ev.status();
        String estado = st != null ? st.estado() : ev.incidente().estado;
        OffsetDateTime inicio = ev.incidente().inicio != null ? ev.incidente().inicio : ev.agora();
        String nome = "*" + ev.nomeLoja() + "*" + sufixoOperacao(ev);
        String desde = "desde " + hora(inicio, zona);

        if (ev.tipo() == TipoEvento.ABERTO) {
            if ("PAUSED".equals(estado)) {
                return "⏸️ " + nome + " " + frase(ev, estado, zona) + " · " + desde;
            }
            if ("WARNING".equals(estado)) {
                return "🟡 " + nome + " " + frase(ev, estado, zona) + " · " + desde;
            }
            return "🔴 " + nome + " fechou — " + motivo(ev, zona, false) + " · " + desde;
        }
        if (ev.tipo() == TipoEvento.MUDOU) {
            return "🔁 " + nome + " agora " + frase(ev, estado, zona) + " · " + desde;
        }
        if (ev.tipo() == TipoEvento.LEMBRETE) {
            return "⏰ " + nome + " ainda " + frase(ev, estado, zona) + " · " + desde
                    + " (há " + duracao(Duration.between(inicio, ev.agora())) + ")";
        }
        String dur = duracao(Duration.between(inicio, ev.agora()));
        String era = "PAUSED".equals(ev.incidente().estado) ? "pausada" : "fechada";
        if ("OK".equals(ev.estadoFinal()) || "WARNING".equals(ev.estadoFinal())) {
            return "✅ " + nome + " voltou (ficou " + dur + " " + era + ")";
        }
        return "⚪ " + nome + " agora fora do horário (ficou " + dur + " " + era + " antes)";
    }

    /** "pausada "Falta de motoboy" até 18:00 + PDV desconectado" ou "fechada — pendência financeira". */
    private static String frase(Evento ev, String estado, ZoneId zona) {
        if ("PAUSED".equals(estado)) {
            String resto = motivo(ev, zona, true);
            String pausa = textoPausa(ev.pausas(), ev.agora(), zona);
            return "pausada" + (pausa.isEmpty() ? "" : " " + pausa) + (resto.isEmpty() ? "" : " + " + resto);
        }
        String palavra = switch (estado) {
            case "WARNING" -> "com restrição";
            case "ERROR", "CLOSED" -> "fechada";
            default -> estado.toLowerCase();
        };
        return palavra + " — " + motivo(ev, zona, false);
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
        LocalDateTime fim = horaLocal(p.fim(), zona);
        if (fim != null) {
            boolean outroDia = !fim.toLocalDate().equals(agora.atZoneSameInstant(zona).toLocalDate());
            partes.add("até " + fim.format(outroDia ? DD_MM_HH_MM : HH_MM));
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
