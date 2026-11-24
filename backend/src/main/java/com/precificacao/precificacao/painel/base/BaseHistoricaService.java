package com.precificacao.precificacao.painel.base;

import com.precificacao.precificacao.painel.BiImportService;
import com.precificacao.precificacao.painel.PainelProperties;
import com.precificacao.precificacao.painel.base.RelatorioPedidos.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Atualiza a base histórica (banco "pedidos" no Railway) com o relatório de pedidos do Portal, fazendo o mesmo
 * caminho do processo manual: public.pedidos → silver.fato_pedido → gold.financeiro_periodo.
 *
 * - public.pedidos: pedido que já existe (mesmo ID completo) é substituído, então reenviar um período não duplica.
 * - silver.fato_pedido: só pedidos de lojas com operação cadastrada (silver.dim_operacao_alias pelo ID da loja).
 * - gold.financeiro_periodo: refaz as semanas (seg–dom) e os meses que o arquivo tocou, com as mesmas contas da
 *   base (pedidos e valores sem os cancelados; repasse = líquido + recebido via loja; etc.). Os valores manuais
 *   (bruto, líquido, conversão, alteração de cardápio…) vêm de silver.fato_metricas_periodo; se não houver,
 *   mantém o que já estava na gold.
 */
@Service
public class BaseHistoricaService {

    private static final Logger log = LoggerFactory.getLogger(BaseHistoricaService.class);
    static final int IFOOD = 1;
    static final int LOTE = 1000;

    public record LojaSemOperacao(String idLoja, String nome, int pedidos) {
    }

    public record Resultado(int lidos, int ignorados, int gravados, int noFato, LocalDate de, LocalDate ate,
                            int semanas, int meses, List<LojaSemOperacao> semOperacao, List<String> colunasFaltando) {
    }

    private final PainelProperties cfg;
    private final BiImportService bi;

    public BaseHistoricaService(PainelProperties cfg, BiImportService bi) {
        this.cfg = cfg;
        this.bi = bi;
    }

    public boolean configurada() {
        return cfg.bancoBiConfigurado();
    }

    /** Lê o arquivo e grava na base. Depois reimporta o histórico do BI no painel (em segundo plano). */
    public Resultado enviar(String nomeArquivo, byte[] conteudo) {
        if (!configurada()) {
            throw new IllegalStateException("BI_DATABASE_URL não configurada");
        }
        RelatorioPedidos.Leitura leitura = RelatorioPedidos.ler(nomeArquivo, conteudo);
        if (leitura.pedidos().isEmpty()) {
            throw new IllegalArgumentException("Nenhum pedido no arquivo");
        }
        BiImportService.Jdbc j = BiImportService.jdbc(cfg.biDatabaseUrl(), BiImportService.BANCO_BI);
        Resultado r;
        try (Connection c = conectar(j)) {
            r = gravar(c, leitura);
        } catch (SQLException e) {
            log.warn("[base] falha ao gravar o relatório", e);
            throw new IllegalStateException("Não consegui gravar na base: " + e.getMessage());
        }
        new Thread(() -> {
            try {
                bi.importar();
            } catch (RuntimeException e) {
                log.warn("[base] reimportação do BI falhou", e);
            }
        }, "base-reimporta-bi").start();
        return r;
    }

    static Connection conectar(BiImportService.Jdbc j) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", j.usuario());
        p.setProperty("password", j.senha());
        p.setProperty("connectTimeout", "15");
        return DriverManager.getConnection(j.url(), p);
    }

    // ------------------------------------------------------------------ gravação

    static Resultado gravar(Connection c, RelatorioPedidos.Leitura leitura) throws SQLException {
        // mesmo pedido duas vezes no arquivo: fica a última linha
        Map<String, Pedido> unicos = new LinkedHashMap<>();
        for (Pedido p : leitura.pedidos()) {
            unicos.put(p.id(), p);
        }
        List<Pedido> pedidos = new ArrayList<>(unicos.values());
        String[] ids = pedidos.stream().map(Pedido::id).toArray(String[]::new);
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            try (Statement st = c.createStatement()) {
                // sem índice, cada reenvio leria a tabela inteira (1 milhão de linhas) para achar os repetidos
                st.execute("CREATE INDEX IF NOT EXISTS idx_pedidos_id_completo_pedido ON public.pedidos (id_completo_pedido)");
            }
            Array arr = c.createArrayOf("varchar", ids);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM public.pedidos WHERE id_completo_pedido = ANY(?)")) {
                ps.setArray(1, arr);
                ps.executeUpdate();
            }
            inserirPedidos(c, pedidos);

            int noFato;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO silver.fato_pedido (id_pedido, id_operacao, id_plataforma, id_data, data_hora_pedido,
                        valor_itens, incentivo_prom_ifood, incentivo_prom_loja, data_carga, status_final_pedido,
                        canal_venda, tipo_entrega)
                    SELECT p.id_completo_pedido, a.id_operacao, a.id_plataforma,
                           to_char(p.data_hora_pedido, 'YYYYMMDD')::int, p.data_hora_pedido,
                           p.valor_itens, p.incentivo_prom_ifood, p.incentivo_prom_loja, now(), p.status_final_pedido,
                           p.canal_venda, p.tipo_entrega
                    FROM public.pedidos p
                    JOIN silver.dim_operacao_alias a ON a.id_loja_origem = p.id_loja AND a.id_plataforma = ?
                    WHERE p.id_completo_pedido = ANY(?)
                    ON CONFLICT (id_pedido) DO UPDATE SET id_operacao = EXCLUDED.id_operacao,
                        id_data = EXCLUDED.id_data, data_hora_pedido = EXCLUDED.data_hora_pedido,
                        valor_itens = EXCLUDED.valor_itens, incentivo_prom_ifood = EXCLUDED.incentivo_prom_ifood,
                        incentivo_prom_loja = EXCLUDED.incentivo_prom_loja, data_carga = EXCLUDED.data_carga,
                        status_final_pedido = EXCLUDED.status_final_pedido, canal_venda = EXCLUDED.canal_venda,
                        tipo_entrega = EXCLUDED.tipo_entrega""")) {
                ps.setInt(1, IFOOD);
                ps.setArray(2, arr);
                noFato = ps.executeUpdate();
            }

            // operações e períodos tocados
            Map<Integer, Set<LocalDate>> diasPorOperacao = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT DISTINCT id_operacao, data_hora_pedido::date FROM silver.fato_pedido
                    WHERE id_pedido = ANY(?) AND id_plataforma = ?""")) {
                ps.setArray(1, arr);
                ps.setInt(2, IFOOD);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        diasPorOperacao.computeIfAbsent(rs.getInt(1), k -> new TreeSet<>()).add(rs.getDate(2).toLocalDate());
                    }
                }
            }
            List<Periodo> periodos = periodos(diasPorOperacao);
            recalcularGold(c, periodos);

            List<LojaSemOperacao> sem = semOperacao(c, arr);
            c.commit();

            LocalDate de = pedidos.stream().map(p -> p.quando().toLocalDate()).min(LocalDate::compareTo).orElse(null);
            LocalDate ate = pedidos.stream().map(p -> p.quando().toLocalDate()).max(LocalDate::compareTo).orElse(null);
            int semanas = (int) periodos.stream().filter(p -> p.tipo().equals("SEMANAL")).map(Periodo::inicio).distinct().count();
            int meses = (int) periodos.stream().filter(p -> p.tipo().equals("MENSAL")).map(Periodo::inicio).distinct().count();
            log.info("[base] relatório gravado: {} pedidos ({} no fato), {}..{}, {} loja(s) sem operação",
                    pedidos.size(), noFato, de, ate, sem.size());
            return new Resultado(leitura.pedidos().size(), leitura.ignoradas(), pedidos.size(), noFato, de, ate,
                    semanas, meses, sem, leitura.colunasFaltando());
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    static void inserirPedidos(Connection c, List<Pedido> pedidos) throws SQLException {
        List<String> cols = RelatorioPedidos.COLUNAS.stream().map(x -> x[0]).toList();
        String sql = "INSERT INTO public.pedidos (" + String.join(", ", cols) + ") VALUES ("
                + cols.stream().map(x -> "?").collect(Collectors.joining(", ")) + ")";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 0;
            for (Pedido p : pedidos) {
                Object[] v = p.valores();
                for (int i = 0; i < cols.size(); i++) {
                    Object x = v[i];
                    if (x == null) {
                        ps.setNull(i + 1, RelatorioPedidos.NUMERICAS.contains(cols.get(i)) ? Types.NUMERIC
                                : cols.get(i).equals("data_hora_pedido") ? Types.TIMESTAMP : Types.VARCHAR);
                    } else if (x instanceof LocalDateTime d) {
                        ps.setTimestamp(i + 1, Timestamp.valueOf(d));
                    } else if (x instanceof BigDecimal b) {
                        ps.setBigDecimal(i + 1, b);
                    } else {
                        ps.setString(i + 1, x.toString());
                    }
                }
                ps.addBatch();
                if (++n % LOTE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    record Periodo(int operacao, String tipo, LocalDate inicio, LocalDate fim) {
    }

    /** Semanas (seg–dom) e meses que contêm os dias tocados de cada operação. */
    static List<Periodo> periodos(Map<Integer, Set<LocalDate>> diasPorOperacao) {
        Set<Periodo> out = new LinkedHashSet<>();
        diasPorOperacao.forEach((op, dias) -> {
            for (LocalDate d : dias) {
                LocalDate seg = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                out.add(new Periodo(op, "SEMANAL", seg, seg.plusDays(6)));
                LocalDate m = d.withDayOfMonth(1);
                out.add(new Periodo(op, "MENSAL", m, m.with(TemporalAdjusters.lastDayOfMonth())));
            }
        });
        return new ArrayList<>(out);
    }

    static void recalcularGold(Connection c, List<Periodo> periodos) throws SQLException {
        if (periodos.isEmpty()) {
            return;
        }
        Array ops = c.createArrayOf("int4", periodos.stream().map(Periodo::operacao).toArray());
        Array tipos = c.createArrayOf("varchar", periodos.stream().map(Periodo::tipo).toArray());
        Array inicios = c.createArrayOf("date", periodos.stream().map(p -> java.sql.Date.valueOf(p.inicio())).toArray());
        Array fins = c.createArrayOf("date", periodos.stream().map(p -> java.sql.Date.valueOf(p.fim())).toArray());
        String alvo = "SELECT * FROM unnest(?::int[], ?::varchar[], ?::date[], ?::date[]) AS t(id_operacao, periodo_tipo, data_inicio, data_fim)";
        try (PreparedStatement ps = c.prepareStatement("""
                WITH alvo AS (%s),
                agg AS (
                  SELECT a.id_operacao, a.periodo_tipo, a.data_inicio, a.data_fim,
                    count(f.id_pedido) FILTER (WHERE f.status_final_pedido <> 'CANCELADO') AS qtd,
                    coalesce(sum(f.valor_itens) FILTER (WHERE f.status_final_pedido <> 'CANCELADO'), 0) AS itens,
                    coalesce(sum(f.incentivo_prom_ifood) FILTER (WHERE f.status_final_pedido <> 'CANCELADO'), 0) AS inc_ifood,
                    coalesce(sum(f.incentivo_prom_loja) FILTER (WHERE f.status_final_pedido <> 'CANCELADO'), 0) AS inc_loja,
                    count(f.id_pedido) FILTER (WHERE f.status_final_pedido = 'CANCELADO') AS canc
                  FROM alvo a
                  LEFT JOIN silver.fato_pedido f ON f.id_operacao = a.id_operacao AND f.id_plataforma = 1
                   AND f.data_hora_pedido >= a.data_inicio AND f.data_hora_pedido < a.data_fim + 1
                  GROUP BY 1, 2, 3, 4)
                INSERT INTO gold.financeiro_periodo (id_operacao, id_plataforma, periodo_tipo, data_inicio, data_fim,
                    qtd_pedidos, valor_itens, incentivos_ifood, incentivos_loja, qtd_cancelamentos,
                    valor_bruto, valor_liquido, recebido_via_loja, repassado_plataforma, conversao, novos_clientes,
                    tempo_entrega, alteracao_cardapio, tempo_aberto, data_carga)
                SELECT g.id_operacao, 1, g.periodo_tipo, g.data_inicio, g.data_fim,
                    g.qtd, g.itens, g.inc_ifood, g.inc_loja, g.canc,
                    m.valor_bruto, m.valor_liquido, m.recebido_via_loja, m.repassado_plataforma, m.conversao,
                    m.novos_clientes, m.tempo_entrega, m.alteracao_cardapio, m.tempo_aberto, now()
                FROM agg g
                LEFT JOIN silver.fato_metricas_periodo m ON m.id_operacao = g.id_operacao AND m.id_plataforma = 1
                 AND m.periodo_tipo = g.periodo_tipo AND m.data_inicio = g.data_inicio
                ON CONFLICT (id_operacao, id_plataforma, periodo_tipo, data_inicio) DO UPDATE SET
                    data_fim = EXCLUDED.data_fim,
                    qtd_pedidos = EXCLUDED.qtd_pedidos, valor_itens = EXCLUDED.valor_itens,
                    incentivos_ifood = EXCLUDED.incentivos_ifood, incentivos_loja = EXCLUDED.incentivos_loja,
                    qtd_cancelamentos = EXCLUDED.qtd_cancelamentos,
                    valor_bruto = coalesce(EXCLUDED.valor_bruto, gold.financeiro_periodo.valor_bruto),
                    valor_liquido = coalesce(EXCLUDED.valor_liquido, gold.financeiro_periodo.valor_liquido),
                    recebido_via_loja = coalesce(EXCLUDED.recebido_via_loja, gold.financeiro_periodo.recebido_via_loja),
                    repassado_plataforma = coalesce(EXCLUDED.repassado_plataforma, gold.financeiro_periodo.repassado_plataforma),
                    conversao = coalesce(EXCLUDED.conversao, gold.financeiro_periodo.conversao),
                    novos_clientes = coalesce(EXCLUDED.novos_clientes, gold.financeiro_periodo.novos_clientes),
                    tempo_entrega = coalesce(EXCLUDED.tempo_entrega, gold.financeiro_periodo.tempo_entrega),
                    alteracao_cardapio = coalesce(EXCLUDED.alteracao_cardapio, gold.financeiro_periodo.alteracao_cardapio),
                    tempo_aberto = coalesce(EXCLUDED.tempo_aberto, gold.financeiro_periodo.tempo_aberto),
                    data_carga = EXCLUDED.data_carga""".formatted(alvo))) {
            ps.setArray(1, ops);
            ps.setArray(2, tipos);
            ps.setArray(3, inicios);
            ps.setArray(4, fins);
            ps.executeUpdate();
        }
        // contas derivadas, iguais às da base
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE gold.financeiro_periodo g SET
                    alteracao_cardapio = coalesce(g.alteracao_cardapio, false),
                    ticket_medio = round(g.valor_itens / nullif(g.qtd_pedidos, 0), 2),
                    perc_incentivos_ifood = round(g.incentivos_ifood / nullif(g.valor_itens, 0), 4),
                    perc_incentivos_loja = round(g.incentivos_loja / nullif(g.valor_itens, 0), 4),
                    repasse = g.valor_liquido + coalesce(g.recebido_via_loja, 0),
                    taxa_repasse = round((g.valor_liquido + coalesce(g.recebido_via_loja, 0))
                                   / nullif(g.valor_bruto - coalesce(g.repassado_plataforma, 0), 0), 4),
                    custo_plataforma = 1 - round((g.valor_liquido + coalesce(g.recebido_via_loja, 0))
                                   / nullif(g.valor_bruto - coalesce(g.repassado_plataforma, 0), 0), 4)
                FROM (%s) a
                WHERE g.id_operacao = a.id_operacao AND g.id_plataforma = 1 AND g.periodo_tipo = a.periodo_tipo
                  AND g.data_inicio = a.data_inicio""".formatted(alvo))) {
            ps.setArray(1, ops);
            ps.setArray(2, tipos);
            ps.setArray(3, inicios);
            ps.setArray(4, fins);
            ps.executeUpdate();
        }
    }

    static List<LojaSemOperacao> semOperacao(Connection c, Array ids) throws SQLException {
        List<LojaSemOperacao> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT p.id_loja, max(p.nome_loja), count(*) FROM public.pedidos p
                LEFT JOIN silver.dim_operacao_alias a ON a.id_loja_origem = p.id_loja AND a.id_plataforma = 1
                WHERE p.id_completo_pedido = ANY(?) AND a.id_alias IS NULL
                GROUP BY 1 ORDER BY 3 DESC""")) {
            ps.setArray(1, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new LojaSemOperacao(rs.getString(1), rs.getString(2), rs.getInt(3)));
                }
            }
        }
        return out;
    }
}
