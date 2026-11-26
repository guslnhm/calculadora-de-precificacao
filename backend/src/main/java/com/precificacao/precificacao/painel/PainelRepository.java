package com.precificacao.precificacao.painel;

import com.precificacao.precificacao.painel.LeitorKpis.Dia;
import com.precificacao.precificacao.painel.LeitorKpis.Hora;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class PainelRepository {

    private final JdbcTemplate jdbc;

    public PainelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------------------------------------------- dias

    @Transactional
    public void salvarDias(String merchantId, Collection<Dia> dias, Map<LocalDate, Integer> novos, OffsetDateTime agora) {
        for (Dia d : dias) {
            jdbc.update("""
                    INSERT INTO painel_dia (merchant_id, dia, pedidos, cancelados, gmv, gmv_cancelado, novos_clientes,
                        incentivo_loja, tempo_entrega, tempo_preparo, atraso, coletado_em, taxa_entrega)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (merchant_id, dia) DO UPDATE SET pedidos = EXCLUDED.pedidos, cancelados = EXCLUDED.cancelados,
                        gmv = EXCLUDED.gmv, gmv_cancelado = EXCLUDED.gmv_cancelado,
                        novos_clientes = COALESCE(EXCLUDED.novos_clientes, painel_dia.novos_clientes),
                        incentivo_loja = EXCLUDED.incentivo_loja, tempo_entrega = EXCLUDED.tempo_entrega,
                        tempo_preparo = EXCLUDED.tempo_preparo, atraso = EXCLUDED.atraso, coletado_em = EXCLUDED.coletado_em,
                        taxa_entrega = EXCLUDED.taxa_entrega
                    """, merchantId, Date.valueOf(d.dia), d.pedidos, d.cancelados, d.gmv, d.gmvCancelado,
                    novos.get(d.dia), d.incentivoLoja, d.tempoEntrega, d.tempoPreparo, d.atraso, agora,
                    d.pedidos == 0 && d.taxaEntrega == null ? BigDecimal.ZERO : d.taxaEntrega);
        }
    }

    public long pedidosNoPeriodo(String merchantId, LocalDate de, LocalDate ate) {
        Long n = jdbc.queryForObject("SELECT COALESCE(sum(pedidos + cancelados), 0) FROM painel_dia "
                + "WHERE merchant_id = ? AND dia BETWEEN ? AND ?", Long.class, merchantId, Date.valueOf(de), Date.valueOf(ate));
        return n == null ? 0 : n;
    }

    /** Pedidos + cancelados de todas as lojas no dia (0 = o iFood ainda não liberou o dia). */
    public long pedidosDoDia(LocalDate dia) {
        Long n = jdbc.queryForObject("SELECT COALESCE(sum(pedidos + cancelados), 0) FROM painel_dia WHERE dia = ?",
                Long.class, Date.valueOf(dia));
        return n == null ? 0 : n;
    }

    /** Pedidos + cancelados de cada loja no dia (só lojas com linha gravada). */
    public Map<String, Integer> movimentoPorLoja(LocalDate dia) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        jdbc.query("SELECT merchant_id, pedidos + cancelados FROM painel_dia WHERE dia = ?",
                rs -> {
                    out.put(rs.getString(1), rs.getInt(2));
                }, Date.valueOf(dia));
        return out;
    }

    /**
     * Lojas que vieram zeradas no dia mas costumam vender nele: venderam no mesmo dia da semana anterior e em
     * pelo menos 3 dos 7 dias antes. Sinal de que o iFood ainda não liberou o dia inteiro para elas.
     */
    public List<String> zeradasSuspeitas(LocalDate dia) {
        return jdbc.queryForList("""
                SELECT d.merchant_id FROM painel_dia d
                WHERE d.dia = ? AND d.pedidos + d.cancelados = 0
                  AND EXISTS (SELECT 1 FROM painel_dia s WHERE s.merchant_id = d.merchant_id AND s.dia = ? AND s.pedidos > 0)
                  AND (SELECT count(*) FROM painel_dia a WHERE a.merchant_id = d.merchant_id
                         AND a.dia BETWEEN ? AND ? AND a.pedidos > 0) >= 3
                ORDER BY d.merchant_id""", String.class,
                Date.valueOf(dia), Date.valueOf(dia.minusDays(7)), Date.valueOf(dia.minusDays(7)), Date.valueOf(dia.minusDays(1)));
    }

    /** Números (pedidos + cancelados por loja) que foram no resumo do dia. */
    public Map<String, Integer> resumoEnviado(LocalDate dia) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        jdbc.query("SELECT merchant_id, movimento FROM painel_resumo_enviado WHERE dia = ?",
                rs -> {
                    out.put(rs.getString(1), rs.getInt(2));
                }, Date.valueOf(dia));
        return out;
    }

    public void salvarResumoEnviado(LocalDate dia, Map<String, Integer> movimento, OffsetDateTime agora) {
        jdbc.update("DELETE FROM painel_resumo_enviado WHERE dia = ?", Date.valueOf(dia));
        movimento.forEach((m, n) -> jdbc.update(
                "INSERT INTO painel_resumo_enviado (dia, merchant_id, movimento, enviado_em) VALUES (?, ?, ?, ?)",
                Date.valueOf(dia), m, n, agora));
    }

    /** Já há números do dia coletados a partir de "desde"? */
    public boolean coletouDiaDesde(LocalDate dia, OffsetDateTime desde) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM painel_dia WHERE dia = ? AND coletado_em >= ?", Integer.class,
                Date.valueOf(dia), desde);
        return n != null && n > 0;
    }

    public LocalDate primeiroDia(String merchantId) {
        return jdbc.query("SELECT min(dia) FROM painel_dia WHERE merchant_id = ?",
                rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null, merchantId);
    }

    public record LinhaDia(String merchantId, LocalDate dia, int pedidos, int cancelados, BigDecimal gmv,
                           BigDecimal gmvCancelado, Integer novosClientes, BigDecimal incentivoLoja,
                           BigDecimal tempoEntrega, BigDecimal tempoPreparo, BigDecimal atraso) {
    }

    public List<LinhaDia> dias(String merchantId, LocalDate de, LocalDate ate) {
        String sql = "SELECT * FROM painel_dia WHERE dia BETWEEN ? AND ?" + (merchantId == null ? "" : " AND merchant_id = ?")
                + " ORDER BY merchant_id, dia";
        Object[] args = merchantId == null ? new Object[]{Date.valueOf(de), Date.valueOf(ate)}
                : new Object[]{Date.valueOf(de), Date.valueOf(ate), merchantId};
        return jdbc.query(sql, (rs, i) -> new LinhaDia(rs.getString("merchant_id"), rs.getDate("dia").toLocalDate(),
                rs.getInt("pedidos"), rs.getInt("cancelados"), rs.getBigDecimal("gmv"), rs.getBigDecimal("gmv_cancelado"),
                (Integer) rs.getObject("novos_clientes"), rs.getBigDecimal("incentivo_loja"),
                rs.getBigDecimal("tempo_entrega"), rs.getBigDecimal("tempo_preparo"), rs.getBigDecimal("atraso")), args);
    }

    // ----------------------------------------------------------------- curva

    @Transactional
    public void salvarCurva(String merchantId, List<Hora> horas, int semanas, OffsetDateTime agora) {
        jdbc.update("DELETE FROM painel_curva_hora WHERE merchant_id = ?", merchantId);
        BigDecimal s = BigDecimal.valueOf(Math.max(1, semanas));
        for (Hora h : horas) {
            jdbc.update("""
                    INSERT INTO painel_curva_hora (merchant_id, dia_semana, hora, gmv_medio, pedidos_medio, semanas, atualizado_em)
                    VALUES (?,?,?,?,?,?,?)
                    ON CONFLICT (merchant_id, dia_semana, hora) DO UPDATE SET gmv_medio = EXCLUDED.gmv_medio,
                        pedidos_medio = EXCLUDED.pedidos_medio, semanas = EXCLUDED.semanas, atualizado_em = EXCLUDED.atualizado_em
                    """, merchantId, h.diaSemana(), h.hora(), h.gmv().divide(s, 2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(h.pedidos()).divide(s, 2, RoundingMode.HALF_UP), semanas, agora);
        }
    }

    public Map<String, Map<Perdas.ChaveHora, BigDecimal>> curvas() {
        Map<String, Map<Perdas.ChaveHora, BigDecimal>> out = new HashMap<>();
        jdbc.query("SELECT merchant_id, dia_semana, hora, gmv_medio FROM painel_curva_hora", rs -> {
            out.computeIfAbsent(rs.getString(1), k -> new HashMap<>())
                    .put(new Perdas.ChaveHora(rs.getInt(2), rs.getInt(3)), rs.getBigDecimal(4));
        });
        return out;
    }

    public OffsetDateTime curvaAtualizadaEm(String merchantId) {
        return jdbc.query("SELECT max(atualizado_em) FROM painel_curva_hora WHERE merchant_id = ?",
                rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null, merchantId);
    }

    // ----------------------------------------------------------------- coleta

    public void coletaOk(String merchantId, OffsetDateTime agora, boolean historicoOk) {
        jdbc.update("""
                INSERT INTO painel_coleta (merchant_id, ultima_coleta, historico_ok) VALUES (?,?,?)
                ON CONFLICT (merchant_id) DO UPDATE SET ultima_coleta = EXCLUDED.ultima_coleta,
                    historico_ok = painel_coleta.historico_ok OR EXCLUDED.historico_ok, ultimo_erro = NULL, erro_em = NULL
                """, merchantId, agora, historicoOk);
    }

    public void coletaErro(String merchantId, String erro, OffsetDateTime agora) {
        jdbc.update("""
                INSERT INTO painel_coleta (merchant_id, ultimo_erro, erro_em) VALUES (?,?,?)
                ON CONFLICT (merchant_id) DO UPDATE SET ultimo_erro = EXCLUDED.ultimo_erro, erro_em = EXCLUDED.erro_em
                """, merchantId, erro == null ? null : erro.substring(0, Math.min(erro.length(), 500)), agora);
    }

    public boolean historicoOk(String merchantId) {
        Boolean b = jdbc.query("SELECT historico_ok FROM painel_coleta WHERE merchant_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, merchantId);
        return Boolean.TRUE.equals(b);
    }

    public List<Map<String, Object>> situacaoColeta() {
        return jdbc.queryForList("""
                SELECT m.merchant_id, m.nome, c.ultima_coleta, c.ultimo_erro, c.erro_em, c.historico_ok,
                       (SELECT min(dia) FROM painel_dia d WHERE d.merchant_id = m.merchant_id) AS desde,
                       (SELECT max(dia) FROM painel_dia d WHERE d.merchant_id = m.merchant_id) AS ate
                FROM ifood_merchant m LEFT JOIN painel_coleta c ON c.merchant_id = m.merchant_id
                WHERE m.ativo ORDER BY m.nome
                """);
    }

    // ----------------------------------------------------------------- incidentes (perdas)

    public record Intervalo(String merchantId, OffsetDateTime inicio, OffsetDateTime fim) {
    }

    /** Incidentes (loja fechada/pausada) que se sobrepõem ao período; os abertos terminam em "agora". */
    public List<Intervalo> incidentes(OffsetDateTime de, OffsetDateTime ate, OffsetDateTime agora) {
        return jdbc.query("""
                SELECT merchant_id, inicio, COALESCE(fim, ?) AS fim FROM ifood_incidente
                WHERE inicio < ? AND COALESCE(fim, ?) > ? ORDER BY merchant_id, inicio
                """, (rs, i) -> new Intervalo(rs.getString(1), rs.getObject(2, OffsetDateTime.class),
                rs.getObject(3, OffsetDateTime.class)), agora, ate, agora, de);
    }

    // ----------------------------------------------------------------- histórico do BI

    @Transactional
    public int salvarBi(List<Map<String, Object>> linhas, OffsetDateTime agora) {
        int n = 0;
        for (Map<String, Object> r : linhas) {
            n += jdbc.update("""
                    INSERT INTO painel_bi_periodo (id_financeiro, id_operacao, plataforma, operacao, loja, periodo_tipo,
                        data_inicio, data_fim, qtd_pedidos, valor_itens, ticket_medio, incentivos_ifood, incentivos_loja,
                        valor_bruto, valor_liquido, repasse, taxa_repasse, conversao, novos_clientes, tempo_entrega,
                        qtd_cancelamentos, tempo_aberto, importado_em, alteracao_cardapio, recebido_via_loja,
                        repassado_plataforma)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (id_financeiro) DO UPDATE SET id_operacao = EXCLUDED.id_operacao,
                        plataforma = EXCLUDED.plataforma, operacao = EXCLUDED.operacao, loja = EXCLUDED.loja,
                        periodo_tipo = EXCLUDED.periodo_tipo, data_inicio = EXCLUDED.data_inicio, data_fim = EXCLUDED.data_fim,
                        qtd_pedidos = EXCLUDED.qtd_pedidos, valor_itens = EXCLUDED.valor_itens,
                        ticket_medio = EXCLUDED.ticket_medio, incentivos_ifood = EXCLUDED.incentivos_ifood,
                        incentivos_loja = EXCLUDED.incentivos_loja, valor_bruto = EXCLUDED.valor_bruto,
                        valor_liquido = EXCLUDED.valor_liquido, repasse = EXCLUDED.repasse,
                        taxa_repasse = EXCLUDED.taxa_repasse, conversao = EXCLUDED.conversao,
                        novos_clientes = EXCLUDED.novos_clientes, tempo_entrega = EXCLUDED.tempo_entrega,
                        qtd_cancelamentos = EXCLUDED.qtd_cancelamentos, tempo_aberto = EXCLUDED.tempo_aberto,
                        importado_em = EXCLUDED.importado_em, alteracao_cardapio = EXCLUDED.alteracao_cardapio,
                        recebido_via_loja = EXCLUDED.recebido_via_loja, repassado_plataforma = EXCLUDED.repassado_plataforma
                    """, r.get("id_financeiro"), r.get("id_operacao"), r.get("plataforma"), r.get("operacao"),
                    r.get("loja"), r.get("periodo_tipo"), r.get("data_inicio"), r.get("data_fim"), r.get("qtd_pedidos"),
                    r.get("valor_itens"), r.get("ticket_medio"), r.get("incentivos_ifood"), r.get("incentivos_loja"),
                    r.get("valor_bruto"), r.get("valor_liquido"), r.get("repasse"), r.get("taxa_repasse"),
                    r.get("conversao"), r.get("novos_clientes"), r.get("tempo_entrega"), r.get("qtd_cancelamentos"),
                    r.get("tempo_aberto"), agora, Boolean.TRUE.equals(r.get("alteracao_cardapio")),
                    r.get("recebido_via_loja"), r.get("repassado_plataforma"));
        }
        return n;
    }

    public record Alias(int idOperacao, String shortId, String nomeOrigem) {
    }

    @Transactional
    public void salvarAliases(List<Alias> aliases) {
        jdbc.update("DELETE FROM painel_operacao_alias");
        for (Alias a : aliases) {
            jdbc.update("""
                    INSERT INTO painel_operacao_alias (id_operacao, short_id, nome_origem) VALUES (?,?,?)
                    ON CONFLICT (id_operacao, short_id) DO UPDATE SET nome_origem = EXCLUDED.nome_origem
                    """, a.idOperacao(), a.shortId() == null ? "" : a.shortId(), a.nomeOrigem());
        }
    }

    public List<Alias> aliases() {
        return jdbc.query("SELECT id_operacao, short_id, nome_origem FROM painel_operacao_alias",
                (rs, i) -> new Alias(rs.getInt(1), rs.getString(2), rs.getString(3)));
    }

    public void salvarShortId(String merchantId, String shortId, OffsetDateTime agora) {
        jdbc.update("""
                INSERT INTO painel_short_id (merchant_id, short_id, atualizado_em) VALUES (?,?,?)
                ON CONFLICT (merchant_id) DO UPDATE SET short_id = EXCLUDED.short_id, atualizado_em = EXCLUDED.atualizado_em
                """, merchantId, shortId, agora);
    }

    /** short id -> merchant id. */
    public Map<String, String> shortIds() {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT short_id, merchant_id FROM painel_short_id", rs -> {
            out.put(rs.getString(1), rs.getString(2));
        });
        return out;
    }

    public boolean temShortId(String merchantId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM painel_short_id WHERE merchant_id = ?", Integer.class,
                merchantId);
        return n != null && n > 0;
    }

    public record OperacaoBi(int idOperacao, String plataforma, String operacao, String loja, String merchantId,
                             String metodo) {
    }

    /** Períodos (início) com alteração de cardápio marcada na base, para a loja do painel. */
    public java.util.Set<LocalDate> alteracoesCardapio(String chave, String tipo, LocalDate de, LocalDate ate) {
        return new java.util.HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT b.data_inicio FROM painel_bi_periodo b
                LEFT JOIN painel_operacao_vinculo v ON v.id_operacao = b.id_operacao
                WHERE b.alteracao_cardapio AND b.periodo_tipo = ? AND b.data_inicio BETWEEN ? AND ?
                  AND COALESCE(v.merchant_id, 'bi:' || b.id_operacao) = ?""", LocalDate.class,
                tipo, java.sql.Date.valueOf(de), java.sql.Date.valueOf(ate), chave));
    }

    public List<OperacaoBi> operacoesBi() {
        return jdbc.query("""
                SELECT DISTINCT ON (b.id_operacao) b.id_operacao, b.plataforma, b.operacao, b.loja, v.merchant_id, v.metodo
                FROM painel_bi_periodo b LEFT JOIN painel_operacao_vinculo v ON v.id_operacao = b.id_operacao
                WHERE b.id_operacao IS NOT NULL
                ORDER BY b.id_operacao, b.data_inicio DESC
                """, (rs, i) -> new OperacaoBi(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6)));
    }

    public void vincular(int idOperacao, String merchantId, String metodo, OffsetDateTime agora) {
        jdbc.update("""
                INSERT INTO painel_operacao_vinculo (id_operacao, merchant_id, metodo, atualizado_em) VALUES (?,?,?,?)
                ON CONFLICT (id_operacao) DO UPDATE SET merchant_id = EXCLUDED.merchant_id, metodo = EXCLUDED.metodo,
                    atualizado_em = EXCLUDED.atualizado_em
                """, idOperacao, merchantId, metodo, agora);
    }

    /** Vínculos manuais não são trocados pela importação automática. */
    public boolean vinculoManual(int idOperacao) {
        Boolean b = jdbc.query("SELECT metodo = 'MANUAL' FROM painel_operacao_vinculo WHERE id_operacao = ?",
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, idOperacao);
        return Boolean.TRUE.equals(b);
    }

    public record PeriodoBi(String chave, String plataforma, String periodoTipo, LocalDate inicio, LocalDate fim,
                            Integer pedidos, BigDecimal faturamento, BigDecimal ticket, BigDecimal repasse,
                            BigDecimal conversao, Integer novosClientes, BigDecimal tempoEntrega, Integer cancelamentos,
                            BigDecimal tempoAberto, BigDecimal incentivosIfood, BigDecimal incentivosLoja,
                            BigDecimal valorBruto, BigDecimal valorLiquido, BigDecimal recebidoViaLoja,
                            BigDecimal repassadoPlataforma) {
    }

    /** Períodos do BI; chave = merchant_id quando vinculado, senão "bi:<id_operacao>". */
    public List<PeriodoBi> periodosBi(String periodoTipo, LocalDate de, LocalDate ate) {
        return jdbc.query("""
                SELECT COALESCE(v.merchant_id, 'bi:' || b.id_operacao) AS chave, b.plataforma, b.periodo_tipo,
                       b.data_inicio, b.data_fim, b.qtd_pedidos, b.valor_itens, b.ticket_medio, b.repasse, b.conversao,
                       b.novos_clientes, b.tempo_entrega, b.qtd_cancelamentos, b.tempo_aberto,
                       b.incentivos_ifood, b.incentivos_loja, b.valor_bruto, b.valor_liquido, b.recebido_via_loja,
                       b.repassado_plataforma
                FROM painel_bi_periodo b LEFT JOIN painel_operacao_vinculo v ON v.id_operacao = b.id_operacao
                WHERE b.periodo_tipo = ? AND b.data_inicio BETWEEN ? AND ?
                """, (rs, i) -> new PeriodoBi(rs.getString("chave"), rs.getString("plataforma"),
                rs.getString("periodo_tipo"), data(rs, "data_inicio"), data(rs, "data_fim"),
                (Integer) rs.getObject("qtd_pedidos"), rs.getBigDecimal("valor_itens"), rs.getBigDecimal("ticket_medio"),
                rs.getBigDecimal("repasse"), rs.getBigDecimal("conversao"), (Integer) rs.getObject("novos_clientes"),
                rs.getBigDecimal("tempo_entrega"), (Integer) rs.getObject("qtd_cancelamentos"),
                rs.getBigDecimal("tempo_aberto"), rs.getBigDecimal("incentivos_ifood"),
                rs.getBigDecimal("incentivos_loja"), rs.getBigDecimal("valor_bruto"), rs.getBigDecimal("valor_liquido"),
                rs.getBigDecimal("recebido_via_loja"), rs.getBigDecimal("repassado_plataforma")), periodoTipo, Date.valueOf(de), Date.valueOf(ate));
    }

    public OffsetDateTime biImportadoEm() {
        return jdbc.query("SELECT max(importado_em) FROM painel_bi_periodo",
                rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null);
    }

    // ----------------------------------------------------------------- manual

    public record Manual(String chave, String periodoTipo, LocalDate inicio, BigDecimal conversao, BigDecimal repasse,
                         OffsetDateTime atualizadoEm, String atualizadoPor, BigDecimal valorBruto,
                         BigDecimal valorLiquido, BigDecimal recebidoViaLoja, BigDecimal repassadoPlataforma) {

        public Manual(String chave, String periodoTipo, LocalDate inicio, BigDecimal conversao, BigDecimal repasse,
                      OffsetDateTime atualizadoEm, String atualizadoPor) {
            this(chave, periodoTipo, inicio, conversao, repasse, atualizadoEm, atualizadoPor, null, null, null, null);
        }
    }

    public void salvarManual(String chave, String periodoTipo, LocalDate inicio, BigDecimal conversao,
                             BigDecimal repasse, String usuario, OffsetDateTime agora) {
        salvarManual(new Manual(chave, periodoTipo, inicio, conversao, repasse, agora, usuario));
    }

    /** Grava (substitui) os valores do Portal da loja no período. */
    public void salvarManual(Manual m) {
        jdbc.update("""
                INSERT INTO painel_manual (chave, periodo_tipo, data_inicio, conversao, repasse, atualizado_em, atualizado_por,
                    valor_bruto, valor_liquido, recebido_via_loja, repassado_plataforma)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (chave, periodo_tipo, data_inicio) DO UPDATE SET conversao = EXCLUDED.conversao,
                    repasse = EXCLUDED.repasse, atualizado_em = EXCLUDED.atualizado_em, atualizado_por = EXCLUDED.atualizado_por,
                    valor_bruto = EXCLUDED.valor_bruto, valor_liquido = EXCLUDED.valor_liquido,
                    recebido_via_loja = EXCLUDED.recebido_via_loja, repassado_plataforma = EXCLUDED.repassado_plataforma
                """, m.chave(), m.periodoTipo(), Date.valueOf(m.inicio()), m.conversao(), m.repasse(), m.atualizadoEm(),
                m.atualizadoPor(), m.valorBruto(), m.valorLiquido(), m.recebidoViaLoja(), m.repassadoPlataforma());
    }

    /** Apaga o lançamento manual (a base histórica passa a valer). */
    public void apagarManual(String chave, String periodoTipo, LocalDate inicio) {
        jdbc.update("DELETE FROM painel_manual WHERE chave = ? AND periodo_tipo = ? AND data_inicio = ?",
                chave, periodoTipo, Date.valueOf(inicio));
    }

    /** Chaves do painel ligadas à operação do BI: a loja vinculada e "bi:<id>". */
    public List<String> chavesDaOperacao(int idOperacao) {
        List<String> out = new ArrayList<>(jdbc.queryForList(
                "SELECT merchant_id FROM painel_operacao_vinculo WHERE id_operacao = ? AND merchant_id IS NOT NULL",
                String.class, idOperacao));
        out.add("bi:" + idOperacao);
        return out;
    }

    /** Operações do BI ligadas à chave do painel (merchant_id ou "bi:<id>"). */
    public List<Integer> operacoesDaChave(String chave) {
        if (chave.startsWith("bi:")) {
            return List.of(Integer.parseInt(chave.substring(3)));
        }
        return jdbc.queryForList("SELECT id_operacao FROM painel_operacao_vinculo WHERE merchant_id = ? ORDER BY 1",
                Integer.class, chave);
    }

    public List<Manual> manuais(String periodoTipo, LocalDate de, LocalDate ate) {
        return jdbc.query("SELECT * FROM painel_manual WHERE periodo_tipo = ? AND data_inicio BETWEEN ? AND ?",
                (rs, i) -> new Manual(rs.getString("chave"), rs.getString("periodo_tipo"), data(rs, "data_inicio"),
                        rs.getBigDecimal("conversao"), rs.getBigDecimal("repasse"),
                        rs.getObject("atualizado_em", OffsetDateTime.class), rs.getString("atualizado_por"),
                        rs.getBigDecimal("valor_bruto"), rs.getBigDecimal("valor_liquido"),
                        rs.getBigDecimal("recebido_via_loja"), rs.getBigDecimal("repassado_plataforma")),
                periodoTipo, Date.valueOf(de), Date.valueOf(ate));
    }

    // ----------------------------------------------------------------- lojas

    public Map<String, String> nomesLojas() {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT merchant_id, nome FROM ifood_merchant", rs -> {
            out.put(rs.getString(1), rs.getString(2));
        });
        return out;
    }

    public List<String> lojasAtivas() {
        return new ArrayList<>(jdbc.queryForList("SELECT merchant_id FROM ifood_merchant WHERE ativo ORDER BY nome",
                String.class));
    }

    private static LocalDate data(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }
}
