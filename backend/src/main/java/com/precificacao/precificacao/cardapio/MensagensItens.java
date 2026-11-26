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

/**
 * Mensagens do monitor de itens: um bloco por loja, separados por uma linha em branco. Ex.:
 * <pre>
 * ⚠️ *Burger Moema*
 * ⏸️ *2 itens pausados* desde 17:00:
 * • X-Bacon
 * • Coca 350ml (complemento)
 *
 * ✅ *Pizzaria Central*
 * ▶️ *Item voltou:* Calabresa (ficou 45 min pausado)
 * </pre>
 */
public final class MensagensItens {

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
        List<String> blocos = new ArrayList<>();
        for (Mudancas m : lojas) {
            boolean temPausa = !m.pausados().isEmpty();
            boolean temVolta = incluirRetornos && !m.voltaram().isEmpty();
            if (!temPausa && !temVolta) {
                continue;
            }
            List<String> l = new ArrayList<>();
            l.add((temPausa ? "⚠️ *" : "✅ *") + m.loja() + "*");
            if (temPausa) {
                boolean soCategorias = m.pausados().stream().allMatch(p -> "CATEGORIA".equals(p.problema()));
                List<String> nomes = m.pausados().stream()
                        .map(p -> (soCategorias ? p.categoria() : nomeCurto(p)) + contexto(p.contexto())).toList();
                OffsetDateTime inicio = m.pausados().stream().map(Pausa::inicio).min(OffsetDateTime::compareTo).orElse(agora);
                String desde = Duration.between(inicio, agora).toMinutes() > 15
                        ? " desde " + inicio.atZoneSameInstant(zona).format(HH_MM) : "";
                lista(l, "⏸️ ", nomes, soCategorias ? "Categoria pausada" : "Item pausado",
                        soCategorias ? "categorias pausadas" : "itens pausados", desde);
            }
            if (temVolta) {
                boolean soCategorias = m.voltaram().stream().allMatch(r -> "CATEGORIA".equals(r.pausa().problema()));
                List<String> nomes = m.voltaram().stream()
                        .map(r -> (soCategorias ? r.pausa().categoria() : nomeCurto(r.pausa())) + contexto(r.pausa().contexto())
                                + " (ficou " + Mensagens.duracao(Duration.between(r.pausa().inicio(), r.fim()))
                                + (soCategorias ? " pausada)" : " pausado)"))
                        .toList();
                lista(l, "▶️ ", nomes, soCategorias ? "Categoria voltou" : "Item voltou",
                        soCategorias ? "categorias voltaram" : "itens voltaram", "");
            }
            blocos.add(String.join("\n", l));
        }
        return String.join(Mensagens.SEPARADOR, blocos);
    }

    /**
     * Resumo do início do dia: o que está pausado agora em cada loja do grupo.
     * <pre>
     * ☀️ *Itens pausados agora* · 18/11
     *
     * ⚠️ *Burger Moema* · 2 pausados
     * • X-Bacon (desde 15/11)
     * • Bebidas (categoria inteira)
     * </pre>
     */
    public static String resumoDoDia(Map<String, List<Pausa>> porLoja, ZoneId zona, OffsetDateTime agora) {
        java.time.LocalDate hoje = agora.atZoneSameInstant(zona).toLocalDate();
        List<String> blocos = new ArrayList<>();
        blocos.add("☀️ *Itens pausados agora* · " + hoje.format(DateTimeFormatter.ofPattern("dd/MM")));
        porLoja.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            List<String> l = new ArrayList<>();
            List<String> nomes = new ArrayList<>(new LinkedHashSet<>(e.getValue().stream()
                    .map(p -> nomeCurto(p) + contexto(p.contexto()) + desde(p.inicio(), hoje, zona)).toList()));
            l.add("⚠️ *" + e.getKey() + "* · " + nomes.size() + (nomes.size() == 1 ? " pausado" : " pausados"));
            nomes.forEach(n -> l.add("• " + n));
            blocos.add(String.join("\n", l));
        });
        return String.join(Mensagens.SEPARADOR, blocos);
    }

    private static String desde(OffsetDateTime inicio, java.time.LocalDate hoje, ZoneId zona) {
        if (inicio == null) {
            return "";
        }
        java.time.ZonedDateTime z = inicio.atZoneSameInstant(zona);
        return z.toLocalDate().equals(hoje) ? " (desde " + z.format(HH_MM) + ")"
                : " (desde " + z.format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
    }

    /** Um nome: "⏸️ *Item pausado:* X-Bacon". Vários: título com a quantidade e uma lista com "•". */
    private static void lista(List<String> l, String emoji, List<String> nomes, String umTitulo, String variosTitulo,
                              String desde) {
        List<String> unicos = new ArrayList<>(new LinkedHashSet<>(nomes.stream().filter(s -> s != null && !s.isBlank()).toList()));
        if (unicos.size() == 1) {
            l.add(emoji + "*" + umTitulo + ":* " + unicos.get(0) + desde);
            return;
        }
        l.add(emoji + "*" + unicos.size() + " " + variosTitulo + "*" + desde + ":");
        unicos.forEach(n -> l.add("• " + n));
    }

    static String nomeCurto(Pausa p) {
        if ("CATEGORIA".equals(p.problema())) {
            return p.categoria() + " (categoria inteira)";
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

    /** " [Salão]" quando a pausa não é no Delivery. */
    static String contexto(String c) {
        if (c == null || "DEFAULT".equalsIgnoreCase(c)) {
            return "";
        }
        return " [" + CONTEXTOS.getOrDefault(c.toUpperCase(), c) + "]";
    }
}
