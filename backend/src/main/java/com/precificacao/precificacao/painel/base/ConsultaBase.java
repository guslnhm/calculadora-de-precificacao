package com.precificacao.precificacao.painel.base;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Consulta livre na base histórica, só leitura: um único comando (SELECT, WITH, EXPLAIN, SHOW, TABLE, VALUES),
 * dentro de uma transação READ ONLY que sempre termina em rollback, com tempo e número de linhas limitados.
 */
public final class ConsultaBase {

    public static final int LIMITE_PADRAO = 1000;
    public static final int LIMITE_MAXIMO = 10000;
    static final Set<String> COMANDOS = Set.of("select", "with", "explain", "show", "table", "values");

    public record Resultado(List<String> colunas, List<List<Object>> linhas, boolean cortado, long ms) {
    }

    private ConsultaBase() {
    }

    /** Tira o ";" do fim, recusa mais de um comando e o que não for leitura. Devolve o SQL limpo. */
    static String validar(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("Escreva a consulta.");
        }
        String s = sql.strip();
        while (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).strip();
        }
        String codigo = semTextos(s);
        if (codigo.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Uma consulta por vez (sem \";\" no meio).");
        }
        String primeira = codigo.strip().replaceFirst("^\\(+", "").strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMANDOS.contains(primeira)) {
            throw new IllegalArgumentException("Aqui só dá para ler: comece com SELECT, WITH, EXPLAIN, SHOW, TABLE ou VALUES.");
        }
        return s;
    }

    /** O SQL sem comentários e sem o conteúdo de textos ('…', "…", $tag$…$tag$), para procurar ";" e o 1º comando. */
    static String semTextos(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
                int f = s.indexOf('\n', i);
                i = f < 0 ? n : f;
            } else if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int f = s.indexOf("*/", i + 2);
                i = f < 0 ? n : f + 2;
                out.append(' ');
            } else if (ch == '\'' || ch == '"') {
                int j = i + 1;
                while (j < n) {
                    if (s.charAt(j) == ch) {
                        if (j + 1 < n && s.charAt(j + 1) == ch) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                out.append(ch).append(ch);
                i = Math.min(j + 1, n);
            } else if (ch == '$') {
                int f = s.indexOf('$', i + 1);
                String tag = f < 0 ? null : s.substring(i, f + 1);
                if (tag != null && tag.matches("\\$[A-Za-z_]*\\$")) {
                    int fim = s.indexOf(tag, f + 1);
                    i = fim < 0 ? n : fim + tag.length();
                    out.append("''");
                } else {
                    out.append(ch);
                    i++;
                }
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    public static Resultado executar(Connection c, String sql, int limite) throws SQLException {
        String s = validar(sql);
        int max = Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        long t0 = System.currentTimeMillis();
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        c.setReadOnly(true);
        try (Statement st = c.createStatement()) {
            st.execute("SET TRANSACTION READ ONLY");
            st.execute("SET LOCAL statement_timeout = '30s'");
            st.setMaxRows(max + 1);
            List<String> colunas = new ArrayList<>();
            List<List<Object>> linhas = new ArrayList<>();
            boolean cortado = false;
            if (st.execute(s)) {
                try (ResultSet rs = st.getResultSet()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int nc = md.getColumnCount();
                    for (int i = 1; i <= nc; i++) {
                        colunas.add(md.getColumnLabel(i));
                    }
                    while (rs.next()) {
                        if (linhas.size() == max) {
                            cortado = true;
                            break;
                        }
                        List<Object> l = new ArrayList<>(nc);
                        for (int i = 1; i <= nc; i++) {
                            Object v = rs.getObject(i);
                            l.add(v == null || v instanceof Number || v instanceof Boolean ? v : rs.getString(i));
                        }
                        linhas.add(l);
                    }
                }
            }
            return new Resultado(colunas, linhas, cortado, System.currentTimeMillis() - t0);
        } finally {
            c.rollback();
            c.setReadOnly(false);
            c.setAutoCommit(auto);
        }
    }
}
