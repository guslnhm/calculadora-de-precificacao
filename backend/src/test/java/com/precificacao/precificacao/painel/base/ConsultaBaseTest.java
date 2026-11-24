package com.precificacao.precificacao.painel.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultaBaseTest {

    Connection c;

    @AfterEach
    void fechar() throws SQLException {
        if (c != null) {
            c.close();
        }
    }

    Connection conectar() throws SQLException {
        String url = System.getenv("SPRING_DATASOURCE_URL");
        Assumptions.assumeTrue(url != null && url.startsWith("jdbc:postgresql://"), "sem Postgres");
        c = DriverManager.getConnection(url, System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres"));
        try (Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS consulta_teste");
            st.execute("CREATE TABLE consulta_teste (id int, nome varchar, quando timestamp, valor numeric)");
            st.execute("INSERT INTO consulta_teste SELECT g, 'n' || g, '2026-11-09 23:49:45', g * 1.5 FROM generate_series(1, 30) g");
        }
        return c;
    }

    @Test
    void validaSoLeituraEUmComando() {
        assertThat(ConsultaBase.validar("  select 1 ;; ")).isEqualTo("select 1");
        assertThat(ConsultaBase.validar("-- comentário\nWITH x AS (SELECT 1) SELECT * FROM x")).startsWith("--");
        assertThat(ConsultaBase.validar("SELECT ';' AS a, $$;$$ AS b /* ; */")).contains("$$;$$");
        assertThat(ConsultaBase.validar("(SELECT 1) UNION (SELECT 2)")).isNotNull();
        assertThatThrownBy(() -> ConsultaBase.validar("SELECT 1; DELETE FROM pedidos"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Uma consulta por vez");
        assertThatThrownBy(() -> ConsultaBase.validar("COMMIT; DELETE FROM pedidos"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsultaBase.validar("DELETE FROM pedidos"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("só dá para ler");
        assertThatThrownBy(() -> ConsultaBase.validar("/* x */ UPDATE pedidos SET id = 1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsultaBase.validar("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executaELimitaAsLinhas() throws SQLException {
        var r = ConsultaBase.executar(conectar(), "SELECT MAX(quando) AS max, count(*) FROM consulta_teste;", 1000);
        assertThat(r.colunas()).containsExactly("max", "count");
        assertThat(r.linhas()).hasSize(1);
        assertThat(r.linhas().get(0).get(0)).isEqualTo("2026-11-09 23:49:45");
        assertThat(((Number) r.linhas().get(0).get(1)).intValue()).isEqualTo(30);
        assertThat(r.cortado()).isFalse();

        var cortado = ConsultaBase.executar(c, "SELECT * FROM consulta_teste ORDER BY id", 10);
        assertThat(cortado.linhas()).hasSize(10);
        assertThat(cortado.cortado()).isTrue();
        assertThat(c.getAutoCommit()).isTrue();
    }

    @Test
    void naoAlteraNadaNaBase() throws SQLException {
        conectar();
        // escrita escondida num WITH: barrada pela transação só leitura
        assertThatThrownBy(() -> ConsultaBase.executar(c, "WITH d AS (DELETE FROM consulta_teste RETURNING *) SELECT count(*) FROM d", 10))
                .isInstanceOf(SQLException.class).hasMessageContaining("read-only");
        assertThatThrownBy(() -> ConsultaBase.executar(c, "SELECT set_config('transaction_read_only', 'off', false)", 10))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> ConsultaBase.executar(c, "EXPLAIN ANALYZE DELETE FROM consulta_teste", 10))
                .isInstanceOf(SQLException.class);
        try (Statement st = c.createStatement(); var rs = st.executeQuery("SELECT count(*) FROM consulta_teste")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(30);
        }
    }
}
