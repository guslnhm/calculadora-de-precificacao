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
                      periodo_tipo varchar, data_inicio date, data_fim date, qtd_pedidos int, valor_itens numeric(12,2),
                      ticket_medio numeric(12,2), incentivos_ifood numeric(12,2), incentivos_loja numeric(12,2),
                      valor_bruto numeric(12,2), valor_liquido numeric(12,2), recebido_via_loja numeric(12,2),
                      repassado_plataforma numeric(12,2), repasse numeric(12,2), taxa_repasse numeric(12,4),
                      custo_plataforma numeric(12,4), conversao numeric(12,4), novos_clientes int, tempo_entrega numeric(10,2),
                      data_carga timestamp DEFAULT now(), perc_incentivos_ifood numeric(12,4), perc_incentivos_loja numeric(12,4),
                      qtd_cancelamentos int, alteracao_cardapio boolean DEFAULT false, tempo_aberto numeric(8,4),
                      CONSTRAINT uk_financeiro_periodo UNIQUE (id_operacao, id_plataforma, periodo_tipo, data_inicio))""");
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
            assertThat(rs.getBigDecimal(8)).isEqualByComparingTo("0.7692");    // 150 / (200 - 5)
            assertThat(rs.getBigDecimal(9)).isEqualByComparingTo("0.2308");
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
}
