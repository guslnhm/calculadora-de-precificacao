package com.precificacao.precificacao.reajuste;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compara nomes de item da calculadora com nomes do iFood, que "nem sempre são iguaizinhos":
 * ignora acento, maiúscula, pontuação e palavras de ligação; junta "350 ml" em "350ml";
 * e trata número/medida diferente (300ml × 500ml) como item diferente.
 */
public final class NomesParecidos {

    private NomesParecidos() {
    }

    private static final Set<String> LIGACAO = Set.of("de", "da", "do", "das", "dos", "com", "e", "o", "a", "os", "as",
            "c", "p", "em", "no", "na", "ao", "para", "pra", "un", "und", "unid", "unidade");
    private static final Pattern MEDIDA = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(ml|l|lt|litro|litros|g|gr|grs|kg|cm|un|unid|pcs|pc|fatias?|pedacos?)\\b");
    private static final Pattern NUMERO = Pattern.compile("\\d+(?:[.,]\\d+)?[a-z]*");

    /** Forma comparável: "Coca-Cola  350 ML" → "coca cola 350ml". */
    public static String normalizar(String nome) {
        if (nome == null) {
            return "";
        }
        String s = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        s = s.replace("&", " e ").replace("+", " mais ");
        Matcher m = MEDIDA.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String unidade = switch (m.group(2)) {
                case "l", "lt", "litro", "litros" -> "l";
                case "gr", "grs" -> "g";
                case "unid", "un" -> "un";
                case "pc", "pcs" -> "pc";
                default -> m.group(2).startsWith("fatia") ? "fatias" : m.group(2).startsWith("pedaco") ? "pedacos" : m.group(2);
            };
            m.appendReplacement(sb, m.group(1).replace(',', '.') + unidade);
        }
        m.appendTail(sb);
        s = sb.toString().replaceAll("[^a-z0-9.]+", " ").replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ");
        List<String> tokens = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) {
            if (!t.isBlank() && !LIGACAO.contains(t)) {
                tokens.add(t);
            }
        }
        return String.join(" ", tokens);
    }

    /**
     * Semelhança de 0 a 1. 1 = mesmo nome depois de normalizar.
     * Mistura palavras em comum (Dice) com pedaços de letras em comum, para tolerar erro de digitação.
     */
    public static double semelhanca(String a, String b) {
        String na = normalizar(a);
        String nb = normalizar(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return 0;
        }
        if (na.equals(nb)) {
            return 1.0;
        }
        Set<String> ta = new LinkedHashSet<>(Arrays.asList(na.split(" ")));
        Set<String> tb = new LinkedHashSet<>(Arrays.asList(nb.split(" ")));
        if (ta.equals(tb)) {
            return 0.97; // mesmas palavras em outra ordem
        }
        double palavras = dice(ta, tb);
        double letras = dice(bigramas(na.replace(" ", "")), bigramas(nb.replace(" ", "")));
        double s = 0.6 * palavras + 0.4 * letras;

        // tamanho/quantidade diferente = produto diferente (Coca 350ml × Coca 2l, Pizza 4 fatias × 8 fatias)
        Set<String> numA = numeros(ta);
        Set<String> numB = numeros(tb);
        if (!numA.isEmpty() && !numB.isEmpty() && !numA.equals(numB)) {
            s *= 0.5;
        } else if (numA.isEmpty() != numB.isEmpty()) {
            s *= 0.85;
        }
        return Math.min(s, 0.96);
    }

    private static Set<String> numeros(Set<String> tokens) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : tokens) {
            if (NUMERO.matcher(t).matches()) {
                out.add(t);
            }
        }
        return out;
    }

    private static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> comum = new LinkedHashSet<>(a);
        comum.retainAll(b);
        return 2.0 * comum.size() / (a.size() + b.size());
    }

    private static Set<String> bigramas(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i + 1 < s.length(); i++) {
            out.add(s.substring(i, i + 2));
        }
        return out;
    }
}
