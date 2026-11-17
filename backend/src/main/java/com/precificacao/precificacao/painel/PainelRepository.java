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
                        incentivo_loja, tempo_entrega, tempo_preparo, atraso, coletado_em)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT (merchant_id, dia) DO UPDATE SET pedidos = EXCLUDED.pedidos, cancelados = EXCLUDED.cancelados,
                        gmv = EXCLUDED.gmv, gmv_cancelado = EXCLUDED.gmv_cancelado,
                        novos_clientes = COALESCE(EXCLUDED.novos_clientes, painel_dia.novos_clientes),
                        incentivo_loja = EXCLUDED.incentivo_loja, tempo_entrega = EXCLUDED.tempo_entrega,
                        tempo_preparo = EXCLUDED.tempo_preparo, atraso = EXCLUDED.atraso, coletado_em = EXCLUDED.coletado_em
                    """, merchantId, Date.valueOf(d.dia), d.pedidos, d.cancelados, d.gmv, d.gmvCancelado,
                    novos.get(d.dia), d.incentivoLoja, d.tempoEntrega, d.tempoPreparo, d.atraso, agora);
        }
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
                        qtd_cancelamentos, tempo_aberto, importado_em)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                        importado_em = EXCLUDED.importado_em
                    """, r.get("id_financeiro"), r.get("id_operacao"), r.get("plataforma"), r.get("operacao"),
                    r.get("loja"), r.get("periodo_tipo"), r.get("data_inicio"), r.get("data_fim"), r.get("qtd_pedidos"),
                    r.get("valor_itens"), r.get("ticket_medio"), r.get("incentivos_ifood"), r.get("incentivos_loja"),
                    r.get("valor_bruto"), r.get("valor_liquido"), r.get("repasse"), r.get("taxa_repasse"),
                    r.get("conversao"), r.get("novos_clientes"), r.get("tempo_entrega"), r.get("qtd_cancelamentos"),
                    r.get("tempo_aberto"), agora);
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
        return Boolean.TRUE.equals(jdbc.query("SELECT 1 FROM painel_short_id WHERE merchant_id = ?",
                rs -> rs.next(), merchantId));
    }

    public record OperacaoBi(int idOperacao, String plataforma, String operacao, String loja, String merchantId,
                             String metodo) {
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
                            BigDecimal tempoAberto, BigDecimal incentivosIfood, BigDecimal incentivosLoja) {
    }

    /** Períodos do BI; chave = merchant_id quando vinculado, senão "bi:<id_operacao>". */
    public List<PeriodoBi> periodosBi(String periodoTipo, LocalDate de, LocalDate ate) {
        return jdbc.query("""
                SELECT COALESCE(v.merchant_id, 'bi:' || b.id_operacao) AS chave, b.plataforma, b.periodo_tipo,
                       b.data_inicio, b.data_fim, b.qtd_pedidos, b.valor_itens, b.ticket_medio, b.repasse, b.conversao,
                       b.novos_clientes, b.tempo_entrega, b.qtd_cancelamentos, b.tempo_aberto,
                       b.incentivos_ifood, b.incentivos_loja
                FROM painel_bi_periodo b LEFT JOIN painel_operacao_vinculo v ON v.id_operacao = b.id_operacao
                WHERE b.periodo_tipo = ? AND b.data_inicio BETWEEN ? AND ?
                """, (rs, i) -> new PeriodoBi(rs.getString("chave"), rs.getString("plataforma"),
                rs.getString("periodo_tipo"), data(rs, "data_inicio"), data(rs, "data_fim"),
                (Integer) rs.getObject("qtd_pedidos"), rs.getBigDecimal("valor_itens"), rs.getBigDecimal("ticket_medio"),
                rs.getBigDecimal("repasse"), rs.getBigDecimal("conversao"), (Integer) rs.getObject("novos_clientes"),
                rs.getBigDecimal("tempo_entrega"), (Integer) rs.getObject("qtd_cancelamentos"),
                rs.getBigDecimal("tempo_aberto"), rs.getBigDecimal("incentivos_ifood"),
                rs.getBigDecimal("incentivos_loja")), periodoTipo, Date.valueOf(de), Date.valueOf(ate));
    }

    public OffsetDateTime biImportadoEm() {
        return jdbc.query("SELECT max(importado_em) FROM painel_bi_periodo",
                rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null);
    }

    // ----------------------------------------------------------------- manual

    public record Manual(String chave, String periodoTipo, LocalDate inicio, BigDecimal conversao, BigDecimal repasse,
                         OffsetDateTime atualizadoEm, String atualizadoPor) {
    }

    public void salvarManual(String chave, String periodoTipo, LocalDate inicio, BigDecimal conversao,
                             BigDecimal repasse, String usuario, OffsetDateTime agora) {
        jdbc.update("""
                INSERT INTO painel_manual (chave, periodo_tipo, data_inicio, conversao, repasse, atualizado_em, atualizado_por)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT (chave, periodo_tipo, data_inicio) DO UPDATE SET conversao = EXCLUDED.conversao,
                    repasse = EXCLUDED.repasse, atualizado_em = EXCLUDED.atualizado_em, atualizado_por = EXCLUDED.atualizado_por
                """, chave, periodoTipo, Date.valueOf(inicio), conversao, repasse, agora, usuario);
    }

    public List<Manual> manuais(String periodoTipo, LocalDate de, LocalDate ate) {
        return jdbc.query("SELECT * FROM painel_manual WHERE periodo_tipo = ? AND data_inicio BETWEEN ? AND ?",
                (rs, i) -> new Manual(rs.getString("chave"), rs.getString("periodo_tipo"), data(rs, "data_inicio"),
                        rs.getBigDecimal("conversao"), rs.getBigDecimal("repasse"),
                        rs.getObject("atualizado_em", OffsetDateTime.class), rs.getString("atualizado_por")),
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
