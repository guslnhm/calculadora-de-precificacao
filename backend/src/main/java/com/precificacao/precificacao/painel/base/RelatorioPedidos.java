package com.precificacao.precificacao.painel.base;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;

/**
 * Lê o "Relatório de pedidos" do Portal do Parceiro: o .xlsx exatamente como o iFood exporta, ou o .csv do
 * processo antigo (sem as colunas de contestação, separado por ";", com ponto no lugar da vírgula).
 *
 * As colunas são achadas pelo nome do cabeçalho, então a ordem e as colunas extras (contestação) não importam.
 * Repete a regra do processo antigo: toda vírgula vira ponto, inclusive nos textos, para os dados novos ficarem
 * iguais aos que já estão na base.
 */
public final class RelatorioPedidos {

    /** As 45 colunas de public.pedidos (sem o id), na ordem da tabela, com o cabeçalho do relatório. */
    public static final List<String[]> COLUNAS = List.of(
            c("id_completo_pedido", "ID COMPLETO DO PEDIDO"),
            c("nome_loja", "NOME DA LOJA"),
            c("id_loja", "ID DA LOJA"),
            c("data_hora_pedido", "DATA E HORA DO PEDIDO"),
            c("turno", "TURNO"),
            c("id_curto", "ID CURTO DO PEDIDO"),
            c("status_final_pedido", "STATUS FINAL DO PEDIDO"),
            c("valor_itens", "VALOR DOS ITENS (R$)"),
            c("total_pago_cliente", "TOTAL PAGO PELO CLIENTE (R$)"),
            c("taxa_entrega_cliente", "TAXA DE ENTREGA PAGA PELO CLIENTE (R$)"),
            c("incentivo_prom_ifood", "INCENTIVO PROMOCIONAL DO IFOOD (R$)"),
            c("incentivo_prom_loja", "INCENTIVO PROMOCIONAL DA LOJA (R$)"),
            c("incentivo_prom_rede", "INCENTIVO PROMOCIONAL DA REDE (R$)"),
            c("taxa_servico", "TAXA DE SERVIÇO (R$)"),
            c("taxas_comissoes", "TAXAS E COMISSOES (R$)"),
            c("valor_liquido", "VALOR LIQUIDO (R$)"),
            c("forma_pagamento", "FORMA DE PAGAMENTO"),
            c("tipo_entrega", "TIPO DE ENTREGA"),
            c("produto_logistico", "PRODUTO LOGISTICO"),
            c("canal_venda", "CANAL DE VENDA"),
            c("tipo_cancelamento", "TIPO DE CANCELAMENTO"),
            c("motivo_cancelamento", "MOTIVO DO CANCELAMENTO"),
            c("origem_cancelamento", "ORIGEM DO CANCELAMENTO"),
            c("data_cancelamento", "DATA DO CANCELAMENTO"),
            c("valor_itens_cancelados", "VALOR DOS ITENS CANCELADOS"),
            c("cancelamento_contestavel", "CANCELAMENTO É CONTESTAVEL"),
            c("motivo_impossibilidade_contestar", "MOTIVO DA IMPOSSIBILIDADE DE CONTESTAR"),
            c("pedido_aceito_loja", "PEDIDO ACEITO PELA LOJA"),
            c("agrupamento_rota", "AGRUPAMENTO DE ROTA"),
            c("prioridade_pedido", "PRIORIDADE DO PEDIDO"),
            c("tempo_preparo_pedido_min", "TEMPO DE PREPARO DO PEDIDO (MIN)"),
            c("tempo_alocacao_entregador_min", "TEMPO DE ALOCAÇÃO DO ENTREGADOR (MIN)"),
            c("tempo_acionamento_botao_pronto_min", "TEMPO DE ACIONAMENTO DO BOTÃO PRONTO (MIN)"),
            c("tempo_entregador_caminho_loja_min", "TEMPO DO ENTREGADOR À CAMINHO DA LOJA (MIN)"),
            c("tempo_entregador_espera_loja_min", "TEMPO DO ENTREGADOR ESPERANDO NA LOJA (MIN)"),
            c("tempo_entregador_caminho_cliente_min", "TEMPO DO ENTREGADOR À CAMINHO DO CLIENTE (MIN)"),
            c("tempo_entregador_espera_cliente_min", "TEMPO DO ENTREGADOR ESPERANDO NO CLIENTE (MIN)"),
            c("tempo_prometido_entrega_min", "TEMPO PROMETIDO DE ENTREGA (MIN)"),
            c("tempo_entrega_realizada_min", "TEMPO DA ENTREGA REALIZADA (MIN)"),
            c("tempo_atraso_relativo_min", "TEMPO DE ATRASO EM RELAÇÃO AO TEMPO PROMETIDO DE ENTREGA (MIN)"),
            c("distancia_percorrida_km", "DISTÂNCIA PERCORRIDA ATÉ O CLIENTE (KM)"),
            c("distancia_considerada", "DISTÂNCIA CONSIDERADA NA COTAÇÃO"),
            c("distancia_considerada_demanda", "DISTÂNCIA CONSIDERADA NA COTAÇÃO (APENAS SOB DEMANDA)"),
            c("frete_cobrado_demanda", "FRETE COBRADO DO RESTAURANTE (APENAS SOB DEMANDA)"),
            c("confirmacao_entrega_propria", "CONFIRMAÇÃO DE ENTREGA PRÓPRIA"));

    /** Colunas numéricas em public.pedidos (o resto é texto). */
    static final java.util.Set<String> NUMERICAS = java.util.Set.of("valor_itens", "total_pago_cliente",
            "taxa_entrega_cliente", "incentivo_prom_ifood", "incentivo_prom_loja", "taxa_servico", "taxas_comissoes",
            "valor_liquido", "valor_itens_cancelados", "tempo_entrega_realizada_min", "distancia_percorrida_km");

    static final int OBRIGATORIAS = 7; // até o status: sem elas não dá para usar a linha

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm[:ss]");

    private static String[] c(String coluna, String cabecalho) {
        return new String[]{coluna, cabecalho};
    }

    /** Um pedido: os 45 valores, já tratados, na ordem de {@link #COLUNAS}. */
    public record Pedido(Object[] valores) {
        public String id() {
            return (String) valores[0];
        }

        public String idLoja() {
            return (String) valores[2];
        }

        public String nomeLoja() {
            return (String) valores[1];
        }

        public LocalDateTime quando() {
            return (LocalDateTime) valores[3];
        }
    }

    public record Leitura(List<Pedido> pedidos, int ignoradas, List<String> colunasFaltando) {
    }

    private RelatorioPedidos() {
    }

    public static Leitura ler(String nomeArquivo, byte[] conteudo) {
        String nome = nomeArquivo == null ? "" : nomeArquivo.toLowerCase(Locale.ROOT);
        boolean xlsx = nome.endsWith(".xlsx") || (conteudo.length > 2 && conteudo[0] == 'P' && conteudo[1] == 'K');
        List<String[]> linhas = xlsx ? lerXlsx(conteudo) : lerCsv(conteudo);
        return montar(linhas);
    }

    // ------------------------------------------------------------------ montar

    static Leitura montar(List<String[]> linhas) {
        if (linhas.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio");
        }
        String[] cab = linhas.get(0);
        Map<String, Integer> pos = new HashMap<>();
        for (int i = 0; i < cab.length; i++) {
            if (cab[i] != null) {
                pos.putIfAbsent(normalizar(cab[i]), i);
            }
        }
        int[] indice = new int[COLUNAS.size()];
        List<String> faltando = new ArrayList<>();
        for (int i = 0; i < COLUNAS.size(); i++) {
            Integer p = pos.get(normalizar(COLUNAS.get(i)[1]));
            indice[i] = p == null ? -1 : p;
            if (p == null) {
                faltando.add(COLUNAS.get(i)[1]);
            }
        }
        for (int i = 0; i < OBRIGATORIAS; i++) {
            if (indice[i] < 0) {
                throw new IllegalArgumentException("Este arquivo não parece o relatório de pedidos do iFood: falta a coluna \""
                        + COLUNAS.get(i)[1] + "\"");
            }
        }
        List<Pedido> pedidos = new ArrayList<>();
        int ignoradas = 0;
        for (int l = 1; l < linhas.size(); l++) {
            String[] r = linhas.get(l);
            Object[] v = new Object[COLUNAS.size()];
            boolean ok = true;
            for (int i = 0; i < COLUNAS.size(); i++) {
                String bruto = indice[i] >= 0 && indice[i] < r.length ? r[indice[i]] : null;
                String coluna = COLUNAS.get(i)[0];
                try {
                    v[i] = converter(coluna, bruto);
                } catch (RuntimeException e) {
                    v[i] = null;
                    if (i < OBRIGATORIAS) {
                        ok = false;
                    }
                }
            }
            if (!ok || v[0] == null || v[2] == null || v[3] == null) {
                if (java.util.Arrays.stream(r).anyMatch(x -> x != null && !x.isBlank())) {
                    ignoradas++;
                }
                continue;
            }
            pedidos.add(new Pedido(v));
        }
        return new Leitura(pedidos, ignoradas, faltando);
    }

    static Object converter(String coluna, String bruto) {
        if (bruto == null) {
            return null;
        }
        String t = bruto.strip();
        if (t.isEmpty()) {
            return NUMERICAS.contains(coluna) || coluna.equals("data_hora_pedido") ? null : "";
        }
        t = t.replace(',', '.'); // regra do processo antigo (Ctrl+H)
        if (coluna.equals("data_hora_pedido")) {
            return dataHora(t);
        }
        if (NUMERICAS.contains(coluna)) {
            return numero(t);
        }
        if (coluna.equals("id_loja") || coluna.equals("id_curto")) {
            // o xlsx traz como texto; se vier como número ("2996749.0"), tira o ".0"
            return t.endsWith(".0") ? t.substring(0, t.length() - 2) : t;
        }
        return t;
    }

    static BigDecimal numero(String t) {
        BigDecimal b = new BigDecimal(t);
        // o xlsx guarda -17.759999999999998: arredonda para 6 casas e tira os zeros
        b = b.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return b.scale() < 0 ? b.setScale(0) : b;
    }

    static LocalDateTime dataHora(String t) {
        try {
            return LocalDateTime.parse(t, DATA_HORA);
        } catch (DateTimeParseException e) {
            // número de série do Excel (dias desde 1899-12-30)
            double serie = Double.parseDouble(t);
            long segundos = Math.round(serie * 86400);
            return LocalDateTime.of(1899, 12, 30, 0, 0).plusSeconds(segundos);
        }
    }

    static String normalizar(String s) {
        String t = Normalizer.normalize(s.strip().replace("﻿", ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        return t.replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------ CSV

    static List<String[]> lerCsv(byte[] conteudo) {
        String texto = new String(conteudo, StandardCharsets.UTF_8);
        if (texto.indexOf('�') >= 0) {
            texto = new String(conteudo, java.nio.charset.Charset.forName("windows-1252"));
        }
        String primeira = texto.lines().findFirst().orElse("");
        char sep = primeira.chars().filter(ch -> ch == ';').count() >= primeira.chars().filter(ch -> ch == ',').count()
                ? ';' : ',';
        List<String[]> out = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            if (aspas) {
                if (ch == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        atual.append('"');
                        i++;
                    } else {
                        aspas = false;
                    }
                } else {
                    atual.append(ch);
                }
            } else if (ch == '"') {
                aspas = true;
            } else if (ch == sep) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                campos.add(atual.toString());
                atual.setLength(0);
                out.add(campos.toArray(new String[0]));
                campos.clear();
            } else {
                atual.append(ch);
            }
        }
        if (atual.length() > 0 || !campos.isEmpty()) {
            campos.add(atual.toString());
            out.add(campos.toArray(new String[0]));
        }
        return out;
    }

    // ------------------------------------------------------------------ XLSX (sem biblioteca: é um zip com XML)

    static List<String[]> lerXlsx(byte[] conteudo) {
        Map<String, byte[]> partes = new HashMap<>();
        // O .xlsx do iFood vem com o tamanho zerado no cabeçalho local de cada arquivo do zip, e o
        // ZipInputStream recusa. Por isso grava num arquivo temporário e lê pelo diretório central (ZipFile).
        java.nio.file.Path tmp = null;
        try {
            tmp = java.nio.file.Files.createTempFile("relatorio-", ".xlsx");
            java.nio.file.Files.write(tmp, conteudo);
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(tmp.toFile())) {
                var entradas = zip.entries();
                while (entradas.hasMoreElements()) {
                    ZipEntry e = entradas.nextElement();
                    String n = e.getName();
                    if (n.equals("xl/sharedStrings.xml") || n.startsWith("xl/worksheets/sheet")) {
                        try (InputStream in = zip.getInputStream(e)) {
                            partes.put(n, ler(in));
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não consegui abrir o .xlsx: " + ex.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tmp);
                } catch (IOException ignorado) {
                    // arquivo temporário: o sistema limpa depois
                }
            }
        }
        byte[] planilha = partes.get("xl/worksheets/sheet1.xml");
        if (planilha == null) {
            planilha = partes.entrySet().stream().filter(x -> x.getKey().startsWith("xl/worksheets/sheet"))
                    .map(Map.Entry::getValue).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("O .xlsx não tem nenhuma planilha"));
        }
        List<String> compartilhadas = partes.containsKey("xl/sharedStrings.xml")
                ? textosCompartilhados(partes.get("xl/sharedStrings.xml")) : List.of();
        try {
            return linhasDaPlanilha(planilha, compartilhadas);
        } catch (XMLStreamException ex) {
            throw new IllegalArgumentException("Não consegui ler a planilha: " + ex.getMessage());
        }
    }

    private static byte[] ler(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toByteArray();
    }

    private static XMLStreamReader xml(byte[] b) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return f.createXMLStreamReader(new ByteArrayInputStream(b));
    }

    static List<String> textosCompartilhados(byte[] b) {
        List<String> out = new ArrayList<>();
        try {
            XMLStreamReader r = xml(b);
            StringBuilder atual = null;
            boolean emT = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String n = r.getLocalName();
                    if (n.equals("si")) {
                        atual = new StringBuilder();
                    } else if (n.equals("t")) {
                        emT = true;
                    } else if (n.equals("rPh")) {
                        // texto fonético (japonês): ignora
                        pular(r);
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                    if (emT && atual != null) {
                        atual.append(r.getText());
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    String n = r.getLocalName();
                    if (n.equals("t")) {
                        emT = false;
                    } else if (n.equals("si")) {
                        out.add(atual == null ? "" : atual.toString());
                        atual = null;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("Não consegui ler os textos do .xlsx: " + e.getMessage());
        }
        return out;
    }

    private static void pular(XMLStreamReader r) throws XMLStreamException {
        int nivel = 1;
        while (nivel > 0 && r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                nivel++;
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                nivel--;
            }
        }
    }

    static List<String[]> linhasDaPlanilha(byte[] b, List<String> compartilhadas) throws XMLStreamException {
        List<String[]> out = new ArrayList<>();
        XMLStreamReader r = xml(b);
        List<String> linha = null;
        int coluna = 0;
        String tipo = null;
        StringBuilder valor = null;
        boolean emValor = false;
        while (r.hasNext()) {
            int ev = r.next();
            if (ev == XMLStreamConstants.START_ELEMENT) {
                switch (r.getLocalName()) {
                    case "row" -> linha = new ArrayList<>();
                    case "c" -> {
                        String ref = r.getAttributeValue(null, "r");
                        coluna = ref == null ? (linha == null ? 0 : linha.size()) : indiceColuna(ref);
                        tipo = r.getAttributeValue(null, "t");
                        valor = new StringBuilder();
                    }
                    case "v", "t" -> emValor = valor != null;
                    default -> {
                    }
                }
            } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                if (emValor) {
                    valor.append(r.getText());
                }
            } else if (ev == XMLStreamConstants.END_ELEMENT) {
                switch (r.getLocalName()) {
                    case "v", "t" -> emValor = false;
                    case "c" -> {
                        if (linha != null && valor != null) {
                            String v = valor.toString();
                            if ("s".equals(tipo) && !v.isEmpty()) {
                                int i = Integer.parseInt(v.strip());
                                v = i < compartilhadas.size() ? compartilhadas.get(i) : "";
                            } else if ((tipo == null || "n".equals(tipo)) && !v.isBlank()) {
                                // célula numérica: escreve como o Excel mostra ("0" e não "0.0"; -17.76 e não -17.759999…)
                                try {
                                    v = numero(v.strip()).toPlainString();
                                } catch (NumberFormatException ignorado) {
                                    // mantém o texto
                                }
                            }
                            while (linha.size() <= coluna) {
                                linha.add(null);
                            }
                            linha.set(coluna, v);
                        }
                        valor = null;
                    }
                    case "row" -> {
                        if (linha != null) {
                            out.add(linha.toArray(new String[0]));
                        }
                        linha = null;
                    }
                    default -> {
                    }
                }
            }
        }
        return out;
    }

    /** "AB12" → 27 (base 0). */
    static int indiceColuna(String ref) {
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char ch = ref.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                break;
            }
            n = n * 26 + (ch - 'A' + 1);
        }
        return n - 1;
    }
}
