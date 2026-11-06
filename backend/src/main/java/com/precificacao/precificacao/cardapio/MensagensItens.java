package com.precificacao.precificacao.cardapio;

import com.precificacao.precificacao.cardapio.CardapioRepository.Pausa;
import com.precificacao.precificacao.monitor.Mensagens;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mensagens curtas do monitor de itens (uma linha por loja), no mesmo estilo das de status:
 * "🍔 *Burger Moema*: X-Bacon e Coca 2L pausados · desde 17:00"
 * "▶️ *Burger Moema*: X-Bacon voltou (45 min)"
 */
public final class MensagensItens {

    static final int MAX_NOMES = 6;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    static final Map<String, String> CONTEXTOS = Map.of("DEFAULT", "Delivery", "INDOOR", "Salão",
            "WHITELABEL", "Cardápio digital");

    private MensagensItens() {
    }

    /** Pausa que voltou, com o fim já preenchido. */
    public record Retorno(Pausa pausa, OffsetDateTime fim) {
    }

    /** O que mudou numa loja num ciclo. */
    public record Mudancas(String merchantId, String loja, List<Pausa> pausados, List<Retorno> voltaram) {
        public boolean vazia() {
            return pausados.isEmpty() && voltaram.isEmpty();
        }
    }

    public static String mensagem(List<Mudancas> lojas, boolean incluirRetornos, ZoneId zona, OffsetDateTime agora) {
        List<String> linhas = new ArrayList<>();
        for (Mudancas m : lojas) {
            String loja = "*" + m.loja() + "*";
            if (!m.pausados().isEmpty()) {
                List<String> nomes = m.pausados().stream().map(MensagensItens::nomeCurto).toList();
                int n = new LinkedHashSet<>(nomes).size();
                OffsetDateTime inicio = m.pausados().stream().map(Pausa::inicio).min(OffsetDateTime::compareTo).orElse(agora);
                String desde = Duration.between(inicio, agora).toMinutes() > 15
                        ? " · desde " + inicio.atZoneSameInstant(zona).format(HH_MM) : "";
                linhas.add("🍔 " + loja + ": " + juntar(nomes) + (n == 1 ? " pausado" : " pausados")
                        + contextos(m.pausados().stream().map(Pausa::contexto).toList()) + desde);
            }
            if (incluirRetornos && !m.voltaram().isEmpty()) {
                List<String> partes = m.voltaram().stream()
                        .map(r -> nomeCurto(r.pausa()) + " (" + Mensagens.duracao(Duration.between(r.pausa().inicio(), r.fim())) + ")")
                        .toList();
                int n = new LinkedHashSet<>(partes).size();
                linhas.add("▶️ " + loja + ": " + juntar(partes) + (n == 1 ? " voltou" : " voltaram")
                        + contextos(m.voltaram().stream().map(r -> r.pausa().contexto()).toList()));
            }
        }
        return String.join("\n", linhas);
    }

    static String nomeCurto(Pausa p) {
        if ("CATEGORIA".equals(p.problema())) {
            return "categoria " + p.categoria();
        }
        String nome = p.nome() == null ? "?" : p.nome();
        if (CardapioModelos.COMPLEMENTO.equals(p.tipo())) {
            nome += " (complemento)";
        }
        if ("NAO_VENDAVEL".equals(p.problema())) {
            nome += " (não vendável)";
        }
        return nome;
    }

    static String juntar(List<String> nomes) {
        List<String> l = new ArrayList<>(new LinkedHashSet<>(nomes.stream().filter(s -> s != null && !s.isBlank()).toList()));
        if (l.size() > MAX_NOMES) {
            return String.join(", ", l.subList(0, MAX_NOMES)) + " e mais " + (l.size() - MAX_NOMES);
        }
        if (l.size() > 1) {
            return String.join(", ", l.subList(0, l.size() - 1)) + " e " + l.get(l.size() - 1);
        }
        return l.isEmpty() ? "" : l.get(0);
    }

    /** " [Salão]" quando a pausa não é (só) no Delivery. */
    static String contextos(List<String> ctxs) {
        Set<String> s = new TreeSet<>();
        for (String c : ctxs) {
            if (c != null && !"DEFAULT".equalsIgnoreCase(c)) {
                s.add(CONTEXTOS.getOrDefault(c.toUpperCase(), c));
            }
        }
        return s.isEmpty() ? "" : " [" + String.join(", ", s) + "]";
    }
}
