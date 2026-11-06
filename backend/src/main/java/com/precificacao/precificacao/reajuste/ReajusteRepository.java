package com.precificacao.precificacao.reajuste;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReajusteRepository {

    /** Item da calculadora que dá o preço de um item/complemento do iFood. */
    public record Vinculo(long id, String merchantId, String tipo, String ifoodId, String ifoodNome, Long itemId,
                          boolean ignorado, String origem, BigDecimal similaridade, OffsetDateTime atualizadoEm,
                          String atualizadoPor) {
        public String chave() {
            return merchantId + "|" + tipo + "|" + ifoodId;
        }
    }

    public record LojaIfood(String merchantId, String nome, Long lojaId) {
    }

    public record Lote(long id, long lojaId, String usuario, OffsetDateTime criadoEm, String situacao, String fonte,
                       int total, int feitas, int falhas, int desfeitas, OffsetDateTime terminadoEm,
                       OffsetDateTime desfeitoEm, String desfeitoPor) {
    }

    private final JdbcTemplate jdbc;

    public ReajusteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------- lojas do iFood de cada loja da calculadora

    /** Todas as lojas do iFood, com a loja da calculadora a que estão ligadas (ou null). */
    public List<LojaIfood> lojasIfood() {
        return jdbc.query("""
                SELECT m.merchant_id, m.nome, li.loja_id
                FROM ifood_merchant m
                LEFT JOIN loja_ifood li ON li.merchant_id = m.merchant_id AND li.ativo
                WHERE m.ativo
                ORDER BY m.nome
                """, (rs, n) -> new LojaIfood(rs.getString("merchant_id"), rs.getString("nome"),
                (Long) rs.getObject("loja_id")));
    }

    public List<LojaIfood> lojasIfoodDaLoja(long lojaId) {
        return jdbc.query("""
                SELECT li.merchant_id, COALESCE(m.nome, li.nome_ifood, li.merchant_id) AS nome, li.loja_id
                FROM loja_ifood li
                LEFT JOIN ifood_merchant m ON m.merchant_id = li.merchant_id
                WHERE li.loja_id = ? AND li.ativo
                ORDER BY 2
                """, (rs, n) -> new LojaIfood(rs.getString("merchant_id"), rs.getString("nome"), rs.getLong("loja_id")),
                lojaId);
    }

    /** Define quais lojas do iFood pertencem à loja da calculadora (tira de outra loja, se estiverem lá). */
    @Transactional
    public void vincularLojas(long lojaId, Collection<String> merchantIds) {
        jdbc.update("DELETE FROM loja_ifood WHERE loja_id = ?", lojaId);
        for (String m : merchantIds) {
            jdbc.update("DELETE FROM loja_ifood WHERE merchant_id = ?", m);
            jdbc.update("""
                    INSERT INTO loja_ifood (loja_id, merchant_id, nome_ifood, ativo, criado_em, atualizado_em)
                    VALUES (?, ?, (SELECT nome FROM ifood_merchant WHERE merchant_id = ?), true, now(), now())
                    """, lojaId, m, m);
        }
    }

    // ------------------------------------------------------------- vínculos de itens

    private static final RowMapper<Vinculo> VINCULO = (rs, n) -> new Vinculo(rs.getLong("id"), rs.getString("merchant_id"),
            rs.getString("tipo"), rs.getString("ifood_id"), rs.getString("ifood_nome"), (Long) rs.getObject("item_id"),
            rs.getBoolean("ignorado"), rs.getString("origem"), rs.getBigDecimal("similaridade"),
            odt(rs.getTimestamp("atualizado_em")), rs.getString("atualizado_por"));

    public Map<String, Vinculo> vinculos(String merchantId) {
        Map<String, Vinculo> m = new HashMap<>();
        jdbc.query("SELECT * FROM item_ifood_vinculo WHERE merchant_id = ?", VINCULO, merchantId)
                .forEach(v -> m.put(v.chave(), v));
        return m;
    }

    public void salvarVinculo(String merchantId, String tipo, String ifoodId, String ifoodNome, Long itemId,
                              boolean ignorado, String origem, BigDecimal similaridade, String usuario, OffsetDateTime agora) {
        jdbc.update("""
                INSERT INTO item_ifood_vinculo (merchant_id, tipo, ifood_id, ifood_nome, item_id, ignorado, origem,
                    similaridade, atualizado_em, atualizado_por)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (merchant_id, tipo, ifood_id) DO UPDATE SET ifood_nome = EXCLUDED.ifood_nome,
                    item_id = EXCLUDED.item_id, ignorado = EXCLUDED.ignorado, origem = EXCLUDED.origem,
                    similaridade = EXCLUDED.similaridade, atualizado_em = EXCLUDED.atualizado_em,
                    atualizado_por = EXCLUDED.atualizado_por
                """, merchantId, tipo, ifoodId, ifoodNome, itemId, ignorado, origem, similaridade, usuario, ts(agora));
    }

    public void apagarVinculo(String merchantId, String tipo, String ifoodId) {
        jdbc.update("DELETE FROM item_ifood_vinculo WHERE merchant_id = ? AND tipo = ? AND ifood_id = ?",
                merchantId, tipo, ifoodId);
    }

    // ------------------------------------------------------------- lotes

    private static final RowMapper<Lote> LOTE = (rs, n) -> new Lote(rs.getLong("id"), rs.getLong("loja_id"),
            rs.getString("usuario"), odt(rs.getTimestamp("criado_em")), rs.getString("situacao"), rs.getString("fonte"),
            rs.getInt("total"), rs.getInt("feitas"), rs.getInt("falhas"), rs.getInt("desfeitas"),
            odt(rs.getTimestamp("terminado_em")), odt(rs.getTimestamp("desfeito_em")), rs.getString("desfeito_por"));

    public long criarLote(long lojaId, String usuario, String fonte, int total, OffsetDateTime agora) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO reajuste_lote (loja_id, usuario, criado_em, situacao, fonte, total)
                    VALUES (?, ?, ?, 'PUBLICANDO', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, lojaId);
            ps.setString(2, usuario);
            ps.setTimestamp(3, ts(agora));
            ps.setString(4, fonte);
            ps.setInt(5, total);
            return ps;
        }, kh);
        Map<String, Object> k = kh.getKeys();
        return ((Number) (k != null && k.containsKey("id") ? k.get("id") : kh.getKey())).longValue();
    }

    public void contar(long loteId, boolean ok) {
        jdbc.update(ok ? "UPDATE reajuste_lote SET feitas = feitas + 1 WHERE id = ?"
                : "UPDATE reajuste_lote SET falhas = falhas + 1 WHERE id = ?", loteId);
    }

    public void contarDesfeita(long loteId) {
        jdbc.update("UPDATE reajuste_lote SET desfeitas = desfeitas + 1 WHERE id = ?", loteId);
    }

    public void situacao(long loteId, String situacao, OffsetDateTime agora) {
        switch (situacao) {
            case "CONCLUIDO" -> jdbc.update("UPDATE reajuste_lote SET situacao = ?, terminado_em = ? WHERE id = ?",
                    situacao, ts(agora), loteId);
            case "DESFEITO" -> jdbc.update("UPDATE reajuste_lote SET situacao = ?, desfeito_em = ? WHERE id = ?",
                    situacao, ts(agora), loteId);
            default -> jdbc.update("UPDATE reajuste_lote SET situacao = ? WHERE id = ?", situacao, loteId);
        }
    }

    public void desfeitoPor(long loteId, String usuario) {
        jdbc.update("UPDATE reajuste_lote SET desfeito_por = ? WHERE id = ?", usuario, loteId);
    }

    public Optional<Lote> lote(long id) {
        return jdbc.query("SELECT * FROM reajuste_lote WHERE id = ?", LOTE, id).stream().findFirst();
    }

    public List<Lote> lotes(long lojaId) {
        return jdbc.query("SELECT * FROM reajuste_lote WHERE loja_id = ? ORDER BY criado_em DESC, id DESC LIMIT 30",
                LOTE, lojaId);
    }

    /** Alterações de um lote (para mostrar o que foi feito e para desfazer). */
    public List<Map<String, Object>> alteracoesDoLote(long loteId) {
        return jdbc.queryForList("""
                SELECT a.id, a.merchant_id, COALESCE(m.nome, a.merchant_id) AS loja, a.tipo, a.entidade_id, a.nome,
                       a.antes, a.depois, a.situacao, a.erro, a.desfaz_id
                FROM cardapio_alteracao a
                LEFT JOIN ifood_merchant m ON m.merchant_id = a.merchant_id
                WHERE a.lote_id = ?
                ORDER BY a.id
                """, loteId);
    }

    /** Lotes que ficaram pela metade (servidor reiniciou no meio): marca como concluídos. */
    public int fecharLotesInterrompidos(OffsetDateTime agora) {
        return jdbc.update("""
                UPDATE reajuste_lote SET situacao = CASE situacao WHEN 'DESFAZENDO' THEN 'DESFEITO' ELSE 'CONCLUIDO' END,
                    terminado_em = COALESCE(terminado_em, ?)
                WHERE situacao IN ('PUBLICANDO', 'DESFAZENDO')
                """, ts(agora));
    }

    public void limparTudo() {
        jdbc.execute("UPDATE cardapio_alteracao SET lote_id = NULL");
        jdbc.execute("TRUNCATE item_ifood_vinculo, reajuste_lote RESTART IDENTITY CASCADE");
        jdbc.execute("DELETE FROM loja_ifood");
    }

    static Timestamp ts(OffsetDateTime t) {
        return t == null ? null : Timestamp.from(t.toInstant());
    }

    static OffsetDateTime odt(Timestamp t) {
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }
}
