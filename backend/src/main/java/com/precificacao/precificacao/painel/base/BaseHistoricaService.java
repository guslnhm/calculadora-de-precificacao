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
 * - silver.fato_pedido: só pedidos de lojas com operação cadastrada (silver.dim_operacao_alias pelo ID da loja;
 *   o script manual também exigia o mesmo nome, e perdia os pedidos de loja que mudou de nome no iFood).
 *   Pedido que já estava no fato é atualizado (o script manual ignorava).
 * - silver.fato_metricas_periodo: cria a linha vazia de cada semana/mês tocado, como o script manual.
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
        reimportarDepois();
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

            Processado pr = processar(c, arr);
            int noFato = pr.noFato();
            List<Periodo> periodos = pr.periodos();

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

    record Processado(int noFato, List<Periodo> periodos) {
    }

    /** Passa os pedidos (por ID completo) de public.pedidos para a fato e refaz a gold dos períodos tocados. */
    static Processado processar(Connection c, Array ids) throws SQLException {
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
            ps.setArray(2, ids);
            noFato = ps.executeUpdate();
        }
        Map<Integer, Set<LocalDate>> diasPorOperacao = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT DISTINCT id_operacao, data_hora_pedido::date FROM silver.fato_pedido
                WHERE id_pedido = ANY(?) AND id_plataforma = ?""")) {
            ps.setArray(1, ids);
            ps.setInt(2, IFOOD);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    diasPorOperacao.computeIfAbsent(rs.getInt(1), k -> new TreeSet<>()).add(rs.getDate(2).toLocalDate());
                }
            }
        }
        List<Periodo> periodos = periodos(diasPorOperacao);
        recalcularGold(c, periodos, false);
        return new Processado(noFato, periodos);
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

    /**
     * Refaz a gold dos períodos. {@code manuaisDaMetrica}: os valores manuais da gold passam a ser exatamente os da
     * métrica (quando a métrica foi editada no site, inclusive para apagar um valor); senão, valor vazio na métrica
     * mantém o que já estava na gold.
     */
    static void recalcularGold(Connection c, List<Periodo> periodos, boolean manuaisDaMetrica) throws SQLException {
        if (periodos.isEmpty()) {
            return;
        }
        Array ops = c.createArrayOf("int4", periodos.stream().map(Periodo::operacao).toArray());
        Array tipos = c.createArrayOf("varchar", periodos.stream().map(Periodo::tipo).toArray());
        Array inicios = c.createArrayOf("date", periodos.stream().map(p -> java.sql.Date.valueOf(p.inicio())).toArray());
        Array fins = c.createArrayOf("date", periodos.stream().map(p -> java.sql.Date.valueOf(p.fim())).toArray());
        String alvo = "SELECT * FROM unnest(?::int[], ?::varchar[], ?::date[], ?::date[]) AS t(id_operacao, periodo_tipo, data_inicio, data_fim)";
        // como no processo manual: cria a linha (vazia) de métricas do período, para os valores do Portal
        // (bruto, líquido, conversão, alteração de cardápio…) serem preenchidos depois
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO silver.fato_metricas_periodo (id_operacao, id_plataforma, periodo_tipo, data_inicio, data_fim)
                SELECT id_operacao, 1, periodo_tipo, data_inicio, data_fim FROM (%s) a
                ON CONFLICT (id_operacao, id_plataforma, periodo_tipo, data_inicio) DO NOTHING""".formatted(alvo))) {
            ps.setArray(1, ops);
            ps.setArray(2, tipos);
            ps.setArray(3, inicios);
            ps.setArray(4, fins);
            ps.executeUpdate();
        }
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
                    %s
                    data_carga = EXCLUDED.data_carga""".formatted(alvo, manuais(manuaisDaMetrica)))) {
            ps.setArray(1, ops);
            ps.setArray(2, tipos);
            ps.setArray(3, inicios);
            ps.setArray(4, fins);
            ps.executeUpdate();
        }
        // ticket, perc. de incentivos, repasse, taxa e custo são colunas geradas pela própria base;
        // aqui só garante o "false" na alteração de cardápio
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE gold.financeiro_periodo g SET alteracao_cardapio = false
                FROM (%s) a
                WHERE g.id_operacao = a.id_operacao AND g.id_plataforma = 1 AND g.periodo_tipo = a.periodo_tipo
                  AND g.data_inicio = a.data_inicio AND g.alteracao_cardapio IS NULL""".formatted(alvo))) {
            ps.setArray(1, ops);
            ps.setArray(2, tipos);
            ps.setArray(3, inicios);
            ps.setArray(4, fins);
            ps.executeUpdate();
        }
    }

    static final List<String> MANUAIS = List.of("valor_bruto", "valor_liquido", "recebido_via_loja",
            "repassado_plataforma", "conversao", "novos_clientes", "tempo_entrega", "alteracao_cardapio", "tempo_aberto");

    static String manuais(boolean daMetrica) {
        return MANUAIS.stream().map(m -> daMetrica
                        ? m + " = EXCLUDED." + m + ","
                        : m + " = coalesce(EXCLUDED." + m + ", gold.financeiro_periodo." + m + "),")
                .collect(Collectors.joining("\n"));
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

    // ------------------------------------------------------------------ métricas manuais (antes preenchidas no banco)

    public record Metrica(int idOperacao, String loja, String operacao, String merchantId, Integer pedidos,
                          BigDecimal faturamento, BigDecimal valorBruto, BigDecimal valorLiquido,
                          BigDecimal recebidoViaLoja, BigDecimal repassadoPlataforma, BigDecimal conversao,
                          Integer novosClientes, BigDecimal tempoEntrega, BigDecimal tempoAberto,
                          boolean alteracaoCardapio, BigDecimal repasse, BigDecimal taxaRepasse) {
    }

    public record EdicaoMetrica(int idOperacao, String periodoTipo, LocalDate inicio, BigDecimal valorBruto,
                                BigDecimal valorLiquido, BigDecimal recebidoViaLoja, BigDecimal repassadoPlataforma,
                                BigDecimal conversao, Integer novosClientes, BigDecimal tempoEntrega,
                                BigDecimal tempoAberto, Boolean alteracaoCardapio) {
    }

    static LocalDate[] periodo(String tipo, LocalDate inicio) {
        if ("MENSAL".equalsIgnoreCase(tipo)) {
            LocalDate m = inicio.withDayOfMonth(1);
            return new LocalDate[]{m, m.with(TemporalAdjusters.lastDayOfMonth())};
        }
        LocalDate seg = inicio.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{seg, seg.plusDays(6)};
    }

    static String tipo(String t) {
        return "MENSAL".equalsIgnoreCase(t) ? "MENSAL" : "SEMANAL";
    }

    /** Operações do iFood na base com as métricas manuais e os números da gold do período. */
    public List<Metrica> metricas(String tipo, LocalDate inicio, Map<Integer, String> merchantPorOperacao) {
        String t = tipo(tipo);
        LocalDate ini = periodo(t, inicio)[0];
        List<Metrica> out = new ArrayList<>();
        try (Connection c = conectar(jdbcBase()); PreparedStatement ps = c.prepareStatement("""
                SELECT o.id_operacao, l.nome_loja, o.nome_operacao, g.qtd_pedidos, g.valor_itens,
                       m.valor_bruto, m.valor_liquido, m.recebido_via_loja, m.repassado_plataforma, m.conversao,
                       m.novos_clientes, m.tempo_entrega, m.tempo_aberto, coalesce(m.alteracao_cardapio, false),
                       g.repasse, g.taxa_repasse
                FROM silver.dim_operacao o
                JOIN silver.dim_loja l ON l.id_loja = o.id_loja
                LEFT JOIN silver.fato_metricas_periodo m ON m.id_operacao = o.id_operacao AND m.id_plataforma = 1
                 AND m.periodo_tipo = ? AND m.data_inicio = ?
                LEFT JOIN gold.financeiro_periodo g ON g.id_operacao = o.id_operacao AND g.id_plataforma = 1
                 AND g.periodo_tipo = ? AND g.data_inicio = ?
                WHERE EXISTS (SELECT 1 FROM silver.dim_operacao_alias a WHERE a.id_operacao = o.id_operacao AND a.id_plataforma = 1)
                ORDER BY l.nome_loja, o.nome_operacao""")) {
            ps.setString(1, t);
            ps.setDate(2, java.sql.Date.valueOf(ini));
            ps.setString(3, t);
            ps.setDate(4, java.sql.Date.valueOf(ini));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int op = rs.getInt(1);
                    out.add(new Metrica(op, rs.getString(2), rs.getString(3), merchantPorOperacao.get(op),
                            (Integer) rs.getObject(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                            rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10), (Integer) rs.getObject(11),
                            rs.getBigDecimal(12), rs.getBigDecimal(13), rs.getBoolean(14), rs.getBigDecimal(15),
                            rs.getBigDecimal(16)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Não consegui ler a base: " + e.getMessage());
        }
        return out;
    }

    /** Grava as métricas do período na silver e refaz a gold dele. Depois reimporta no painel. */
    public void salvarMetrica(EdicaoMetrica e) {
        String t = tipo(e.periodoTipo());
        LocalDate[] p = periodo(t, e.inicio());
        validar(e);
        try (Connection c = conectar(jdbcBase())) {
            c.setAutoCommit(false);
            try {
                gravarMetrica(c, e, t, p[0], p[1]);
                recalcularGold(c, List.of(new Periodo(e.idOperacao(), t, p[0], p[1])), true);
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Não consegui gravar na base: " + ex.getMessage());
        }
        reimportarDepois();
    }

    static void validar(EdicaoMetrica e) {
        if (e.conversao() != null && (e.conversao().signum() < 0 || e.conversao().compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Conversão deve estar entre 0% e 100%");
        }
        if (e.tempoAberto() != null && (e.tempoAberto().signum() < 0 || e.tempoAberto().compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Tempo aberto deve estar entre 0% e 100%");
        }
    }

    static void gravarMetrica(Connection c, EdicaoMetrica e, String tipo, LocalDate ini, LocalDate fim) throws SQLException {
        // a taxa de repasse da base é (líquido + recebido) / (bruto − repassado): com recebido ou repassado
        // em branco ela fica vazia, então, havendo bruto ou líquido, o que ficou em branco vale 0
        boolean temValores = e.valorBruto() != null || e.valorLiquido() != null;
        BigDecimal recebido = e.recebidoViaLoja() == null && temValores ? BigDecimal.ZERO : e.recebidoViaLoja();
        BigDecimal repassado = e.repassadoPlataforma() == null && temValores ? BigDecimal.ZERO : e.repassadoPlataforma();
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO silver.fato_metricas_periodo (id_operacao, id_plataforma, periodo_tipo, data_inicio, data_fim,
                    valor_bruto, valor_liquido, recebido_via_loja, repassado_plataforma, conversao, novos_clientes,
                    tempo_entrega, tempo_aberto, alteracao_cardapio, data_carga)
                VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id_operacao, id_plataforma, periodo_tipo, data_inicio) DO UPDATE SET
                    data_fim = EXCLUDED.data_fim, valor_bruto = EXCLUDED.valor_bruto, valor_liquido = EXCLUDED.valor_liquido,
                    recebido_via_loja = EXCLUDED.recebido_via_loja, repassado_plataforma = EXCLUDED.repassado_plataforma,
                    conversao = EXCLUDED.conversao, novos_clientes = EXCLUDED.novos_clientes,
                    tempo_entrega = EXCLUDED.tempo_entrega, tempo_aberto = EXCLUDED.tempo_aberto,
                    alteracao_cardapio = EXCLUDED.alteracao_cardapio, data_carga = EXCLUDED.data_carga""")) {
            ps.setInt(1, e.idOperacao());
            ps.setString(2, tipo);
            ps.setDate(3, java.sql.Date.valueOf(ini));
            ps.setDate(4, java.sql.Date.valueOf(fim));
            ps.setBigDecimal(5, e.valorBruto());
            ps.setBigDecimal(6, e.valorLiquido());
            ps.setBigDecimal(7, recebido);
            ps.setBigDecimal(8, repassado);
            ps.setBigDecimal(9, e.conversao());
            ps.setObject(10, e.novosClientes(), Types.INTEGER);
            ps.setBigDecimal(11, e.tempoEntrega());
            ps.setBigDecimal(12, e.tempoAberto());
            ps.setBoolean(13, Boolean.TRUE.equals(e.alteracaoCardapio()));
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ lojas sem operação

    public record Operacao(int idOperacao, String loja, String operacao, Integer idLoja) {
    }

    public record NovaOperacao(String idLojaOrigem, String nomeOrigem, Integer idOperacao, Integer idLoja,
                               String nomeLoja, String nomeOperacao, String tipoEntrega) {
    }

    /** Lojas com pedidos na base que ainda não têm operação (ficam fora da fato e da gold). */
    public List<LojaSemOperacao> semOperacao() {
        try (Connection c = conectar(jdbcBase()); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT p.id_loja, max(p.nome_loja), count(*) FROM public.pedidos p
                     WHERE NOT EXISTS (SELECT 1 FROM silver.dim_operacao_alias a
                                       WHERE a.id_loja_origem = p.id_loja AND a.id_plataforma = 1)
                     GROUP BY 1 ORDER BY 3 DESC""")) {
            List<LojaSemOperacao> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new LojaSemOperacao(rs.getString(1), rs.getString(2), rs.getInt(3)));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Não consegui ler a base: " + e.getMessage());
        }
    }

    public List<Operacao> operacoes() {
        try (Connection c = conectar(jdbcBase()); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT o.id_operacao, l.nome_loja, o.nome_operacao, l.id_loja FROM silver.dim_operacao o
                     JOIN silver.dim_loja l ON l.id_loja = o.id_loja ORDER BY l.nome_loja, o.nome_operacao""")) {
            List<Operacao> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new Operacao(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Não consegui ler a base: " + e.getMessage());
        }
    }

    /**
     * Liga a loja do relatório (ID da loja no iFood) a uma operação: existente ou nova (com loja nova ou existente).
     * Em seguida passa todos os pedidos dela para a fato e refaz a gold. Devolve quantos pedidos entraram.
     */
    public int cadastrarOperacao(NovaOperacao n) {
        if (n.idLojaOrigem() == null || n.idLojaOrigem().isBlank()) {
            throw new IllegalArgumentException("Falta o ID da loja");
        }
        int r;
        try (Connection c = conectar(jdbcBase())) {
            c.setAutoCommit(false);
            try {
                r = cadastrarOperacao(c, n);
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Não consegui gravar na base: " + ex.getMessage());
        }
        reimportarDepois();
        return r;
    }

    static int cadastrarOperacao(Connection c, NovaOperacao n) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM silver.dim_operacao_alias WHERE id_loja_origem = ? AND id_plataforma = 1")) {
            ps.setString(1, n.idLojaOrigem().strip());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("Essa loja já tem operação");
                }
            }
        }
        int op;
        if (n.idOperacao() != null) {
            op = n.idOperacao();
        } else {
            String nomeOperacao = n.nomeOperacao() == null ? "" : n.nomeOperacao().strip();
            if (nomeOperacao.isEmpty()) {
                throw new IllegalArgumentException("Dê um nome para a operação (ex.: Asa Norte)");
            }
            int idLoja;
            if (n.idLoja() != null) {
                idLoja = n.idLoja();
            } else {
                String nomeLoja = n.nomeLoja() == null ? "" : n.nomeLoja().strip();
                if (nomeLoja.isEmpty()) {
                    throw new IllegalArgumentException("Escolha a marca (loja) ou digite o nome de uma nova");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO silver.dim_loja (nome_loja) VALUES (?) RETURNING id_loja")) {
                    ps.setString(1, nomeLoja);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        idLoja = rs.getInt(1);
                    }
                }
            }
            Integer tipoEntrega = null;
            if (n.tipoEntrega() != null && !n.tipoEntrega().isBlank()) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id_tipo_entrega FROM silver.dim_tipo_entrega WHERE lower(tipo_entrega) = lower(?)")) {
                    ps.setString(1, n.tipoEntrega().strip());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            tipoEntrega = rs.getInt(1);
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO silver.dim_operacao (id_loja, nome_operacao, id_tipo_entrega) VALUES (?, ?, ?) RETURNING id_operacao")) {
                ps.setInt(1, idLoja);
                ps.setString(2, nomeOperacao);
                ps.setObject(3, tipoEntrega, Types.INTEGER);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    op = rs.getInt(1);
                }
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO silver.dim_operacao_alias (id_operacao, id_plataforma, nome_origem, id_loja_origem)
                VALUES (?, 1, ?, ?)""")) {
            ps.setInt(1, op);
            ps.setString(2, n.nomeOrigem());
            ps.setString(3, n.idLojaOrigem().strip());
            ps.executeUpdate();
        }
        List<String> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id_completo_pedido FROM public.pedidos WHERE id_loja = ?")) {
            ps.setString(1, n.idLojaOrigem().strip());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
        }
        if (ids.isEmpty()) {
            return 0;
        }
        return processar(c, c.createArrayOf("varchar", ids.toArray())).noFato();
    }

    // ------------------------------------------------------------------ apoio

    private BiImportService.Jdbc jdbcBase() {
        if (!configurada()) {
            throw new IllegalStateException("BI_DATABASE_URL não configurada");
        }
        return BiImportService.jdbc(cfg.biDatabaseUrl(), BiImportService.BANCO_BI);
    }

    private void reimportarDepois() {
        new Thread(() -> {
            try {
                bi.importar();
            } catch (RuntimeException e) {
                log.warn("[base] reimportação do BI falhou", e);
            }
        }, "base-reimporta-bi").start();
    }
}
