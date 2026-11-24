package com.precificacao.precificacao.painel.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gravação na base histórica, num banco separado com o mesmo formato das tabelas do banco "pedidos"
 * (dados fictícios).
 */
class BaseHistoricaTest {

    static final String BANCO = "pedidos_teste";
    Connection c;

    @BeforeEach
    void preparar() throws SQLException {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        Assumptions.assumeTrue(url != null && url.startsWith("jdbc:postgresql://"), "sem Postgres");
        String usuario = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres");
        String senha = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres");
        String servidor = url.substring(0, url.lastIndexOf('/') + 1);
        try (Connection adm = DriverManager.getConnection(servidor + "postgres", usuario, senha);
             Statement st = adm.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + BANCO + " WITH (FORCE)");
            st.execute("CREATE DATABASE " + BANCO);
        }
        c = DriverManager.getConnection(servidor + BANCO, usuario, senha);
        try (Statement st = c.createStatement()) {
            StringBuilder cols = new StringBuilder("id serial PRIMARY KEY");
            for (String[] col : RelatorioPedidos.COLUNAS) {
                String tipo = RelatorioPedidos.NUMERICAS.contains(col[0]) ? "numeric"
                        : col[0].equals("data_hora_pedido") ? "timestamp" : "varchar";
                cols.append(", ").append(col[0]).append(' ').append(tipo);
            }
            st.execute("CREATE TABLE public.pedidos (" + cols + ")");
            st.execute("CREATE SCHEMA silver");
            st.execute("CREATE SCHEMA gold");
            st.execute("""
                    CREATE TABLE silver.dim_operacao_alias (id_alias serial PRIMARY KEY, id_operacao int NOT NULL,
                      id_plataforma int NOT NULL, nome_origem varchar, id_loja_origem varchar)""");
            st.execute("""
                    CREATE TABLE silver.fato_pedido (id_pedido varchar PRIMARY KEY, id_operacao int NOT NULL,
                      id_plataforma int NOT NULL, id_data int NOT NULL, data_hora_pedido timestamp,
                      valor_itens numeric(12,2), incentivo_prom_ifood numeric(12,2), incentivo_prom_loja numeric(12,2),
                      data_carga timestamp DEFAULT now(), status_final_pedido varchar, canal_venda varchar, tipo_entrega varchar)""");
            st.execute("""
                    CREATE TABLE silver.fato_metricas_periodo (id_metrica bigserial PRIMARY KEY, id_operacao int NOT NULL,
                      id_plataforma int NOT NULL, periodo_tipo varchar NOT NULL, data_inicio date NOT NULL, data_fim date NOT NULL,
                      valor_bruto numeric(12,2), valor_liquido numeric(12,2), recebido_via_loja numeric(12,2),
                      repassado_plataforma numeric(12,2), conversao numeric(8,4), novos_clientes int, tempo_entrega numeric(8,2),
                      data_carga timestamp DEFAULT now(), alteracao_cardapio boolean DEFAULT false, tempo_aberto numeric(8,4),
                      UNIQUE (id_operacao, id_plataforma, periodo_tipo, data_inicio))""");
            st.execute("""
                    CREATE TABLE gold.financeiro_periodo (id_financeiro bigserial PRIMARY KEY, id_operacao int, id_plataforma int,
                      periodo_tipo varchar, data_inicio date, data_fim date, qtd_pedidos int, valor_itens numeric,
                      ticket_medio numeric GENERATED ALWAYS AS (valor_itens / NULLIF(qtd_pedidos, 0)::numeric) STORED,
                      incentivos_ifood numeric, incentivos_loja numeric,
                      valor_bruto numeric, valor_liquido numeric, recebido_via_loja numeric, repassado_plataforma numeric,
                      repasse numeric GENERATED ALWAYS AS (COALESCE(valor_liquido, 0) + COALESCE(recebido_via_loja, 0)) STORED,
                      taxa_repasse numeric GENERATED ALWAYS AS ((valor_liquido + recebido_via_loja)
                        / NULLIF(valor_bruto - repassado_plataforma, 0)) STORED,
                      custo_plataforma numeric GENERATED ALWAYS AS (1 - (valor_liquido + recebido_via_loja)
                        / NULLIF(valor_bruto - repassado_plataforma, 0)) STORED,
                      conversao numeric, novos_clientes int, tempo_entrega numeric,
                      data_carga timestamp DEFAULT now(),
                      perc_incentivos_ifood numeric GENERATED ALWAYS AS (incentivos_ifood / NULLIF(valor_itens, 0)) STORED,
                      perc_incentivos_loja numeric GENERATED ALWAYS AS (incentivos_loja / NULLIF(valor_itens, 0)) STORED,
                      qtd_cancelamentos int, alteracao_cardapio boolean DEFAULT false, tempo_aberto numeric,
                      CONSTRAINT uk_financeiro_periodo UNIQUE (id_operacao, id_plataforma, periodo_tipo, data_inicio))""");
            st.execute("CREATE TABLE silver.dim_loja (id_loja serial PRIMARY KEY, nome_loja varchar)");
            st.execute("CREATE TABLE silver.dim_tipo_entrega (id_tipo_entrega serial PRIMARY KEY, tipo_entrega varchar UNIQUE)");
            st.execute("INSERT INTO silver.dim_tipo_entrega (tipo_entrega) VALUES ('Própria'), ('Parceira')");
            st.execute("""
                    CREATE TABLE silver.dim_operacao (id_operacao serial PRIMARY KEY, id_loja int NOT NULL,
                      nome_operacao varchar, id_tipo_entrega int, super boolean DEFAULT false)""");
            st.execute("INSERT INTO silver.dim_loja (nome_loja) VALUES ('Marca A')");
            st.execute("SELECT setval('silver.dim_operacao_id_operacao_seq', 10)");
            st.execute("INSERT INTO silver.dim_operacao_alias (id_operacao, id_plataforma, nome_origem, id_loja_origem) VALUES (7, 1, 'Loja A', '111')");
            // semana de 16/11 com valores manuais (bruto, líquido, recebido…) e alteração de cardápio
            st.execute("""
                    INSERT INTO silver.fato_metricas_periodo (id_operacao, id_plataforma, periodo_tipo, data_inicio, data_fim,
                      valor_bruto, valor_liquido, recebido_via_loja, repassado_plataforma, conversao, alteracao_cardapio)
                    VALUES (7, 1, 'SEMANAL', '2026-11-16', '2026-11-22', 200, 140, 10, 5, 0.25, true)""");
            // mês de novembro já existia na gold com bruto preenchido e sem métrica: deve ser mantido
            st.execute("""
                    INSERT INTO gold.financeiro_periodo (id_operacao, id_plataforma, periodo_tipo, data_inicio, data_fim,
                      qtd_pedidos, valor_bruto, valor_liquido) VALUES (7, 1, 'MENSAL', '2026-11-01', '2026-11-30', 1, 999, 700)""");
        }
    }

    @AfterEach
    void fechar() throws SQLException {
        if (c != null) {
            c.close();
        }
    }

    RelatorioPedidos.Leitura relatorio(String... linhas) {
        StringBuilder s = new StringBuilder(RelatorioPedidosTest.cabecalhoCsv()).append('\n');
        for (String l : linhas) {
            s.append(l).append('\n');
        }
        return RelatorioPedidos.ler("r.csv", s.toString().getBytes(StandardCharsets.UTF_8));
    }

    Object um(String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getObject(1);
        }
    }

    @Test
    void gravaPedidosFatoEGoldSemDuplicar() throws SQLException {
        var leitura = relatorio(
                RelatorioPedidosTest.linhaCsv("p-1", "Loja A", "111", "17/11/2026 20:00:00", "CONCLUIDO", "100"),
                RelatorioPedidosTest.linhaCsv("p-2", "Loja A", "111", "18/11/2026 20:00:00", "CANCELAMENTO PARCIAL", "60"),
                RelatorioPedidosTest.linhaCsv("p-3", "Loja A", "111", "19/11/2026 20:00:00", "CANCELADO", "40"),
                RelatorioPedidosTest.linhaCsv("p-4", "Loja Nova", "999", "19/11/2026 20:00:00", "CONCLUIDO", "30"));
        var r = BaseHistoricaService.gravar(c, leitura);
        assertThat(r.gravados()).isEqualTo(4);
        assertThat(r.noFato()).isEqualTo(3);
        assertThat(r.semOperacao()).hasSize(1);
        assertThat(r.semOperacao().get(0).idLoja()).isEqualTo("999");
        assertThat(r.semanas()).isEqualTo(1);
        assertThat(r.meses()).isEqualTo(1);

        // semana: 2 pedidos (sem o cancelado), R$ 160, 1 cancelamento; manuais da métrica e contas derivadas
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT qtd_pedidos, valor_itens, ticket_medio, qtd_cancelamentos, incentivos_ifood, valor_bruto, repasse,
                       taxa_repasse, custo_plataforma, conversao, alteracao_cardapio, data_fim
                FROM gold.financeiro_periodo WHERE periodo_tipo = 'SEMANAL'""")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(2);
            assertThat(rs.getBigDecimal(2)).isEqualByComparingTo("160");
            assertThat(rs.getBigDecimal(3)).isEqualByComparingTo("80");
            assertThat(rs.getInt(4)).isEqualTo(1);
            assertThat(rs.getBigDecimal(5)).isEqualByComparingTo("4");
            assertThat(rs.getBigDecimal(6)).isEqualByComparingTo("200");
            assertThat(rs.getBigDecimal(7)).isEqualByComparingTo("150");       // 140 + 10
            assertThat(rs.getBigDecimal(8).setScale(4, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("0.7692");    // 150 / (200 - 5)
            assertThat(rs.getBigDecimal(9).setScale(4, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("0.2308");
            assertThat(rs.getBigDecimal(10)).isEqualByComparingTo("0.25");
            assertThat(rs.getBoolean(11)).isTrue();
            assertThat(rs.getDate(12).toString()).isEqualTo("2026-11-22");
        }
        // mês: recalcula os pedidos, mas mantém o bruto/líquido que já estavam (não há métrica do mês)
        assertThat(um("SELECT qtd_pedidos FROM gold.financeiro_periodo WHERE periodo_tipo = 'MENSAL'")).isEqualTo(2);
        assertThat((BigDecimal) um("SELECT valor_bruto FROM gold.financeiro_periodo WHERE periodo_tipo = 'MENSAL'"))
                .isEqualByComparingTo("999");
        assertThat((BigDecimal) um("SELECT repasse FROM gold.financeiro_periodo WHERE periodo_tipo = 'MENSAL'"))
                .isEqualByComparingTo("700");
        assertThat(um("SELECT alteracao_cardapio FROM gold.financeiro_periodo WHERE periodo_tipo = 'MENSAL'")).isEqualTo(false);
        // linha vazia de métricas criada para o mês; a da semana (já preenchida) continua igual
        assertThat(um("SELECT count(*) FROM silver.fato_metricas_periodo")).isEqualTo(2L);
        assertThat((BigDecimal) um("SELECT valor_bruto FROM silver.fato_metricas_periodo WHERE periodo_tipo = 'SEMANAL'"))
                .isEqualByComparingTo("200");

        // reenviar o mesmo período (com um pedido que mudou de status) não duplica
        var r2 = BaseHistoricaService.gravar(c, relatorio(
                RelatorioPedidosTest.linhaCsv("p-1", "Loja A", "111", "17/11/2026 20:00:00", "CANCELADO", "100")));
        assertThat(r2.gravados()).isEqualTo(1);
        assertThat(um("SELECT count(*) FROM public.pedidos")).isEqualTo(4L);
        assertThat(um("SELECT count(*) FROM silver.fato_pedido")).isEqualTo(3L);
        assertThat(um("SELECT qtd_pedidos FROM gold.financeiro_periodo WHERE periodo_tipo = 'SEMANAL'")).isEqualTo(1);
        assertThat(um("SELECT qtd_cancelamentos FROM gold.financeiro_periodo WHERE periodo_tipo = 'SEMANAL'")).isEqualTo(2);
        assertThat(um("SELECT count(*) FROM gold.financeiro_periodo")).isEqualTo(2L);
    }

    @Test
    void editarMetricaNoSiteSobrescreveAGold() throws SQLException {
        BaseHistoricaService.gravar(c, relatorio(
                RelatorioPedidosTest.linhaCsv("p-1", "Loja A", "111", "17/11/2026 20:00:00", "CONCLUIDO", "100")));
        var e = new BaseHistoricaService.EdicaoMetrica(7, "SEMANAL", java.time.LocalDate.of(2026, 11, 19),
                new BigDecimal("300"), new BigDecimal("210"), null, null, new BigDecimal("0.2"), 12, null, null, false);
        BaseHistoricaService.gravarMetrica(c, e, "SEMANAL", java.time.LocalDate.of(2026, 11, 16), java.time.LocalDate.of(2026, 11, 22));
        BaseHistoricaService.recalcularGold(c, java.util.List.of(new BaseHistoricaService.Periodo(7, "SEMANAL",
                java.time.LocalDate.of(2026, 11, 16), java.time.LocalDate.of(2026, 11, 22))), true);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT valor_bruto, recebido_via_loja, repasse, taxa_repasse, alteracao_cardapio, novos_clientes, qtd_pedidos
                FROM gold.financeiro_periodo WHERE periodo_tipo = 'SEMANAL'""")) {
            rs.next();
            assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("300");
            assertThat(rs.getBigDecimal(2)).isEqualByComparingTo("0");    // em branco com bruto/líquido preenchidos: 0
            assertThat(rs.getBigDecimal(3)).isEqualByComparingTo("210");
            assertThat(rs.getBigDecimal(4)).isEqualByComparingTo("0.7");  // 210 / (300 - 0)
            assertThat(rs.getBoolean(5)).isFalse();
            assertThat(rs.getInt(6)).isEqualTo(12);
            assertThat(rs.getInt(7)).isEqualTo(1);
        }
    }

    @Test
    void cadastrarOperacaoTrazOsPedidosParaAGold() throws SQLException {
        var r = BaseHistoricaService.gravar(c, relatorio(
                RelatorioPedidosTest.linhaCsv("n-1", "Loja Nova", "999", "17/11/2026 20:00:00", "CONCLUIDO", "50"),
                RelatorioPedidosTest.linhaCsv("n-2", "Loja Nova", "999", "02/12/2026 20:00:00", "CONCLUIDO", "70")));
        assertThat(r.noFato()).isZero();
        int n = BaseHistoricaService.cadastrarOperacao(c, new BaseHistoricaService.NovaOperacao("999", "Loja Nova",
                null, null, "Marca Nova", "Centro", "Parceira"));
        assertThat(n).isEqualTo(2);
        assertThat(um("SELECT nome_loja FROM silver.dim_loja WHERE id_loja = (SELECT id_loja FROM silver.dim_operacao WHERE nome_operacao = 'Centro')"))
                .isEqualTo("Marca Nova");
        assertThat(um("SELECT count(*) FROM gold.financeiro_periodo WHERE id_operacao = 11")).isEqualTo(4L); // 2 semanas e 2 meses
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BaseHistoricaService.cadastrarOperacao(c,
                        new BaseHistoricaService.NovaOperacao("999", "Loja Nova", 7, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valoresDoBoletimMantemOsOutrosCamposDaMetrica() throws SQLException {
        BaseHistoricaService.gravar(c, relatorio(
                RelatorioPedidosTest.linhaCsv("p-1", "Loja A", "111", "17/11/2026 20:00:00", "CONCLUIDO", "100")));
        try (Statement st = c.createStatement()) {
            st.execute("UPDATE silver.fato_metricas_periodo SET novos_clientes = 9 WHERE periodo_tipo = 'SEMANAL'");
        }
        java.time.LocalDate ini = java.time.LocalDate.of(2026, 11, 16);
        java.time.LocalDate fim = java.time.LocalDate.of(2026, 11, 22);
        BaseHistoricaService.gravarValoresPortal(c, 7, "SEMANAL", ini, fim, new BigDecimal("0.3"),
                new BigDecimal("400"), new BigDecimal("300"), null, new BigDecimal("25"));
        BaseHistoricaService.recalcularGold(c, java.util.List.of(new BaseHistoricaService.Periodo(7, "SEMANAL", ini, fim)), true);
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
                SELECT valor_bruto, recebido_via_loja, repassado_plataforma, repasse, taxa_repasse, novos_clientes,
                       alteracao_cardapio, conversao
                FROM gold.financeiro_periodo WHERE periodo_tipo = 'SEMANAL'""")) {
            rs.next();
            assertThat(rs.getBigDecimal(1)).isEqualByComparingTo("400");
            assertThat(rs.getBigDecimal(2)).isEqualByComparingTo("0");
            assertThat(rs.getBigDecimal(3)).isEqualByComparingTo("25");
            assertThat(rs.getBigDecimal(4)).isEqualByComparingTo("300");
            assertThat(rs.getBigDecimal(5).setScale(4, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("0.8000"); // 300 / 375
            assertThat(rs.getInt(6)).isEqualTo(9);
            assertThat(rs.getBoolean(7)).isTrue();
            assertThat(rs.getBigDecimal(8)).isEqualByComparingTo("0.3");
        }
        // só conversão: não apaga os valores
        BaseHistoricaService.gravarValoresPortal(c, 7, "SEMANAL", ini, fim, new BigDecimal("0.1"), null, null, null, null);
        assertThat((BigDecimal) um("SELECT valor_bruto FROM silver.fato_metricas_periodo WHERE periodo_tipo = 'SEMANAL'"))
                .isEqualByComparingTo("400");
        assertThat((BigDecimal) um("SELECT conversao FROM silver.fato_metricas_periodo WHERE periodo_tipo = 'SEMANAL'"))
                .isEqualByComparingTo("0.1");
    }
}
