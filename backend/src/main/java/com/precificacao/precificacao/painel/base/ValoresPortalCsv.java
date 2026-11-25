package com.precificacao.precificacao.painel.base;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Planilha (CSV com ";") dos valores financeiros do Portal por operação e período:
 * id_operacao; [loja]; [periodo_tipo]; inicio; [valor_bruto; valor_liquido; recebido_via_loja; repassado_ifood];
 * [conversao; novos_clientes; tempo_entrega; tempo_aberto]. Conversão e tempo aberto de 0 a 1 (0,2606 = 26,06%).
 * Célula em branco não muda o valor que já está na base (exceto os financeiros, como antes).
 * Números em reais com vírgula ou ponto; datas em dd/mm/aaaa ou aaaa-mm-dd. Colunas extras são ignoradas.
 */
public final class ValoresPortalCsv {

    private ValoresPortalCsv() {
    }

    public static List<BaseHistoricaService.ValoresPortal> ler(byte[] conteudo) {
        String texto = new String(conteudo, StandardCharsets.UTF_8).replace("﻿", "");
        String[] linhas = texto.split("\\r?\\n");
        if (linhas.length < 2) {
            throw new IllegalArgumentException("Planilha vazia.");
        }
        String sep = linhas[0].contains(";") ? ";" : ",";
        String[] cab = linhas[0].split(sep, -1);
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < cab.length; i++) {
            col.put(cab[i].strip().toLowerCase(Locale.ROOT).replace("\"", ""), i);
        }
        int op = indice(col, "id_operacao");
        int ini = indice(col, "inicio", "semana", "data_inicio");
        Integer bruto = col.get("valor_bruto");
        Integer liq = col.get("valor_liquido");
        Integer conv = col.get("conversao");
        Integer novos = col.get("novos_clientes");
        Integer entrega = col.get("tempo_entrega");
        Integer aberto = col.get("tempo_aberto");
        if (bruto == null && liq == null && conv == null && novos == null && entrega == null && aberto == null) {
            throw new IllegalArgumentException("Falta a coluna valor_bruto (ou conversao, novos_clientes, tempo_entrega, tempo_aberto).");
        }
        Integer rec = col.get("recebido_via_loja");
        Integer repd = col.containsKey("repassado_ifood") ? col.get("repassado_ifood") : col.get("repassado_plataforma");
        Integer tipo = col.get("periodo_tipo");
        List<BaseHistoricaService.ValoresPortal> out = new ArrayList<>();
        for (int n = 1; n < linhas.length; n++) {
            if (linhas[n].isBlank()) {
                continue;
            }
            String[] c = linhas[n].split(sep, -1);
            try {
                out.add(new BaseHistoricaService.ValoresPortal(Integer.parseInt(campo(c, op)),
                        tipo == null || campo(c, tipo).isEmpty() ? "SEMANAL" : campo(c, tipo),
                        data(campo(c, ini)), opcional(c, bruto), opcional(c, liq), opcional(c, rec), opcional(c, repd),
                        opcional(c, conv), inteiro(opcional(c, novos)), opcional(c, entrega), opcional(c, aberto)));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Linha " + (n + 1) + " inválida: " + e.getMessage());
            }
        }
        return out;
    }

    private static int indice(Map<String, Integer> col, String... nomes) {
        for (String n : nomes) {
            if (col.containsKey(n)) {
                return col.get(n);
            }
        }
        throw new IllegalArgumentException("Falta a coluna " + nomes[0] + ".");
    }

    private static String campo(String[] c, int i) {
        return i < c.length ? c[i].strip().replace("\"", "") : "";
    }

    private static BigDecimal opcional(String[] c, Integer i) {
        return i == null ? null : numero(campo(c, i));
    }

    private static Integer inteiro(BigDecimal v) {
        return v == null ? null : v.intValueExact();
    }

    static LocalDate data(String s) {
        return s.contains("/") ? LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/yyyy")) : LocalDate.parse(s);
    }

    static BigDecimal numero(String s) {
        String t = s.replace("R$", "").replace(" ", "").replace(" ", "");
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains(",")) {
            t = t.replace(".", "").replace(",", ".");
        }
        return new BigDecimal(t);
    }
}
