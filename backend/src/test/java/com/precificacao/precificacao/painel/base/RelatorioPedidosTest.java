package com.precificacao.precificacao.painel.base;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Leitura do relatório de pedidos (dados fictícios). */
class RelatorioPedidosTest {

    static String cabecalhoCsv() {
        return String.join(";", RelatorioPedidos.COLUNAS.stream().map(c -> c[1]).toList());
    }

    static String linhaCsv(String id, String loja, String idLoja, String quando, String status, String itens) {
        List<String> v = new ArrayList<>();
        for (String[] c : RelatorioPedidos.COLUNAS) {
            v.add(switch (c[0]) {
                case "id_completo_pedido" -> id;
                case "nome_loja" -> loja;
                case "id_loja" -> idLoja;
                case "data_hora_pedido" -> quando;
                case "status_final_pedido" -> status;
                case "valor_itens" -> itens;
                case "incentivo_prom_ifood" -> "2";
                case "incentivo_prom_loja" -> "1.5";
                case "canal_venda" -> "iFood";
                case "tipo_entrega" -> "ENTREGA";
                default -> "";
            });
        }
        return String.join(";", v);
    }

    @Test
    void leCsvDoProcessoAntigo() {
        String csv = "﻿" + cabecalhoCsv() + "\r\n"
                + linhaCsv("p-1", "Loja Teste. Centro", "111", "23/11/2026 21:43:23", "CONCLUIDO", "77.8") + "\r\n"
                + linhaCsv("p-2", "Loja Teste. Centro", "111", "23/11/2026 12:00:00", "CANCELADO", "10") + "\r\n";
        var r = RelatorioPedidos.ler("relatorio.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(r.pedidos()).hasSize(2);
        assertThat(r.colunasFaltando()).isEmpty();
        var p = r.pedidos().get(0);
        assertThat(p.id()).isEqualTo("p-1");
        assertThat(p.idLoja()).isEqualTo("111");
        assertThat(p.quando()).isEqualTo(LocalDateTime.of(2026, 11, 23, 21, 43, 23));
        assertThat(p.valores()[7]).isEqualTo(new BigDecimal("77.8"));
        assertThat(p.valores()[20]).isEqualTo(""); // tipo de cancelamento vazio vira texto vazio
        assertThat(p.valores()[38]).isNull();      // número vazio vira nulo
    }

    @Test
    void leXlsxDoPortalComColunasDeContestacaoEVirgulaNoTexto() throws IOException {
        // cabeçalho do Portal: 50 colunas (as 45 + 5 de contestação no meio)
        List<String> cab = new ArrayList<>(RelatorioPedidos.COLUNAS.stream().map(c -> c[1]).toList());
        cab.add(26, "CONTESTÁVEL ATÉ");
        cab.add(27, "DENTRO DO PRAZO DE CONTESTAÇÃO");
        cab.add(29, "HOUVE CONTESTAÇÃO");
        cab.add(30, "STATUS DA CONTESTAÇÃO");
        cab.add(31, "RETORNO DA CONTESTAÇÃO");
        List<String> textos = new ArrayList<>(cab);
        textos.add("abc-123");                       // id
        textos.add("Kebab, Shawarma & Cia");          // loja (com vírgula e &)
        textos.add("2996749");                        // id loja
        textos.add("23/11/2026 21:43:23");            // data
        textos.add("CONCLUIDO");
        int base = cab.size();
        StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        sheet.append("<row r=\"1\">");
        for (int i = 0; i < cab.size(); i++) {
            sheet.append("<c r=\"").append(col(i)).append("1\" t=\"s\"><v>").append(i).append("</v></c>");
        }
        sheet.append("</row><row r=\"2\">");
        sheet.append(cel(0, 2, "s", base)).append(cel(1, 2, "s", base + 1)).append(cel(2, 2, "s", base + 2))
                .append(cel(3, 2, "s", base + 3)).append(cel(6, 2, "s", base + 4))
                .append("<c r=\"H2\" t=\"n\"><v>77.8</v></c>")
                .append("<c r=\"O2\" t=\"n\"><v>-17.759999999999998</v></c>");
        sheet.append("</row></sheetData></worksheet>");
        StringBuilder sst = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        for (String t : textos) {
            sst.append("<si><t>").append(t.replace("&", "&amp;")).append("</t></si>");
        }
        sst.append("</sst>");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bytes)) {
            z.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            z.write(sst.toString().getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            z.write(sheet.toString().getBytes(StandardCharsets.UTF_8));
        }
        var r = RelatorioPedidos.ler("relatorio-pedidos.xlsx", bytes.toByteArray());
        assertThat(r.pedidos()).hasSize(1);
        var p = r.pedidos().get(0);
        assertThat(p.nomeLoja()).isEqualTo("Kebab. Shawarma & Cia"); // mesma regra do Ctrl+H
        assertThat(p.idLoja()).isEqualTo("2996749");
        assertThat(p.valores()[7]).isEqualTo(new BigDecimal("77.8"));
        assertThat(p.valores()[14]).isEqualTo(new BigDecimal("-17.76"));
        assertThat(p.valores()[6]).isEqualTo("CONCLUIDO");
    }

    @Test
    void arquivoQueNaoEOrelatorio() {
        assertThatThrownBy(() -> RelatorioPedidos.ler("x.csv", "a;b;c\n1;2;3\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ID COMPLETO DO PEDIDO");
    }

    @Test
    void colunaDoExcel() {
        assertThat(RelatorioPedidos.indiceColuna("A1")).isZero();
        assertThat(RelatorioPedidos.indiceColuna("AB12")).isEqualTo(27);
        assertThat(RelatorioPedidos.indiceColuna("AX2")).isEqualTo(49);
    }

    static String col(int i) {
        StringBuilder s = new StringBuilder();
        int n = i + 1;
        while (n > 0) {
            int r = (n - 1) % 26;
            s.insert(0, (char) ('A' + r));
            n = (n - 1) / 26;
        }
        return s.toString();
    }

    static String cel(int coluna, int linha, String tipo, int v) {
        return "<c r=\"" + col(coluna) + linha + "\" t=\"" + tipo + "\"><v>" + v + "</v></c>";
    }
}
