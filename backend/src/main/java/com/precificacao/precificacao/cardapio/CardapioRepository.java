package com.precificacao.precificacao.cardapio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Linha;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

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
public class CardapioRepository {

    /** Situação salva de um item/complemento (da leitura anterior). */
    public record Estado(String chave, String status, String statusCategoria, String naoVendavel, BigDecimal preco,
                         String nome, String categoria, String problema, OffsetDateTime desde, String contexto) {
    }

    /** Período de pausa de um item/complemento. */
    public record Pausa(long id, String merchantId, String catalogId, String tipo, String entidadeId, String contexto,
                        String nome, String categoria, String problema, String detalhe, OffsetDateTime inicio,
                        OffsetDateTime fim, boolean avisado) {
    }

    public record Alteracao(long id, String merchantId, String tipo, String entidadeId, String nome, String campo,
                            String antes, String depois, String usuario, OffsetDateTime feitoEm, String situacao,
                            String erro, OffsetDateTime desfeitaEm, String desfeitaPor, Long desfazId, Long loteId) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CardapioRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------- cardápio lido

    public void salvarCardapio(Cardapio c) {
        jdbc.update("""
                INSERT INTO ifood_cardapio (merchant_id, lido_em, conteudo, itens, pausados, erro, erro_em)
                VALUES (?, ?, ?::jsonb, ?, ?, NULL, NULL)
                ON CONFLICT (merchant_id) DO UPDATE SET lido_em = EXCLUDED.lido_em, conteudo = EXCLUDED.conteudo,
                    itens = EXCLUDED.itens, pausados = EXCLUDED.pausados, erro = NULL, erro_em = NULL
                """, c.merchantId(), ts(c.lidoEm()), json(c), c.totalItens(), c.totalPausados());
    }

    /** Guarda o erro da última leitura sem apagar o cardápio anterior. */
    public void erroLeitura(String merchantId, String erro, OffsetDateTime agora) {
        jdbc.update("""
                INSERT INTO ifood_cardapio (merchant_id, lido_em, conteudo, erro, erro_em)
                VALUES (?, ?, '{"catalogos":[]}'::jsonb, ?, ?)
                ON CONFLICT (merchant_id) DO UPDATE SET erro = EXCLUDED.erro, erro_em = EXCLUDED.erro_em
                """, merchantId, ts(agora), erro, ts(agora));
    }

    public Optional<Cardapio> cardapio(String merchantId) {
        List<Cardapio> l = jdbc.query("SELECT merchant_id, lido_em, conteudo::text AS conteudo FROM ifood_cardapio "
                + "WHERE merchant_id = ? AND erro_em IS DISTINCT FROM lido_em", (rs, n) -> {
            Cardapio c = ler(rs.getString("conteudo"));
            return new Cardapio(rs.getString("merchant_id"), odt(rs.getTimestamp("lido_em")),
                    c.catalogos() == null ? List.of() : c.catalogos());
        }, merchantId);
        return l.stream().findFirst();
    }

    public Optional<Map<String, Object>> infoLeitura(String merchantId) {
        return jdbc.queryForList("SELECT lido_em, itens, pausados, erro, erro_em FROM ifood_cardapio WHERE merchant_id = ?",
                merchantId).stream().findFirst();
    }

    /** Uma linha por loja: nome, quando o cardápio foi lido, quantos itens e quantos parados. */
    public List<Map<String, Object>> resumoLojas() {
        return jdbc.queryForList("""
                SELECT m.merchant_id, m.nome AS loja, c.lido_em, COALESCE(c.itens, 0) AS itens,
                       COALESCE(c.pausados, 0) AS pausados, c.erro, c.erro_em
                FROM ifood_merchant m
                LEFT JOIN ifood_cardapio c ON c.merchant_id = m.merchant_id
                WHERE m.ativo
                ORDER BY m.nome
                """);
    }

    // ------------------------------------------------------------- estado dos itens

    public Map<String, Estado> estados(String merchantId) {
        Map<String, Estado> m = new HashMap<>();
        jdbc.query("SELECT * FROM ifood_item_estado WHERE merchant_id = ?", rs -> {
            String chave = rs.getString("catalog_id") + "|" + rs.getString("tipo") + "|" + rs.getString("entidade_id");
            m.put(chave, new Estado(chave, rs.getString("status"), rs.getString("status_categoria"),
                    rs.getString("nao_vendavel"), rs.getBigDecimal("preco"), rs.getString("nome"),
                    rs.getString("categoria"), rs.getString("problema"), odt(rs.getTimestamp("desde")),
                    rs.getString("contexto")));
        }, merchantId);
        return m;
    }

    public void salvarEstados(List<Linha> linhas, Map<String, String> problemas, Map<String, OffsetDateTime> desde,
                              OffsetDateTime agora) {
        if (linhas.isEmpty()) {
            return;
        }
        jdbc.batchUpdate("""
                INSERT INTO ifood_item_estado (merchant_id, catalog_id, tipo, entidade_id, contexto, nome, categoria, status,
                    status_categoria, nao_vendavel, preco, problema, desde, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (merchant_id, catalog_id, tipo, entidade_id) DO UPDATE SET contexto = EXCLUDED.contexto,
                    nome = EXCLUDED.nome, categoria = EXCLUDED.categoria, status = EXCLUDED.status,
                    status_categoria = EXCLUDED.status_categoria, nao_vendavel = EXCLUDED.nao_vendavel,
                    preco = EXCLUDED.preco, problema = EXCLUDED.problema, desde = EXCLUDED.desde,
                    atualizado_em = EXCLUDED.atualizado_em
                """, linhas, 200, (ps, l) -> {
            ps.setString(1, l.merchantId());
            ps.setString(2, l.catalogId());
            ps.setString(3, l.tipo());
            ps.setString(4, l.entidadeId());
            ps.setString(5, l.contexto());
            ps.setString(6, l.nome());
            ps.setString(7, l.categoria());
            ps.setString(8, l.status());
            ps.setString(9, l.statusCategoria());
            ps.setString(10, l.naoVendavel());
            ps.setBigDecimal(11, l.preco());
            ps.setString(12, problemas.get(l.chave()));
            ps.setTimestamp(13, ts(desde.getOrDefault(l.chave(), agora)));
            ps.setTimestamp(14, ts(agora));
        });
    }

    public void apagarEstados(String merchantId, Collection<String> chaves) {
        for (String chave : chaves) {
            String[] p = chave.split("\\|", 3);
            jdbc.update("DELETE FROM ifood_item_estado WHERE merchant_id = ? AND catalog_id = ? AND tipo = ? AND entidade_id = ?",
                    merchantId, p[0], p[1], p[2]);
        }
    }

    // ------------------------------------------------------------- pausas

    private static final RowMapper<Pausa> PAUSA = (rs, n) -> new Pausa(rs.getLong("id"), rs.getString("merchant_id"),
            rs.getString("catalog_id"), rs.getString("tipo"), rs.getString("entidade_id"), rs.getString("contexto"),
            rs.getString("nome"), rs.getString("categoria"), rs.getString("problema"), rs.getString("detalhe"),
            odt(rs.getTimestamp("inicio")), odt(rs.getTimestamp("fim")), rs.getBoolean("avisado"));

    public void abrirPausa(Linha l, String problema, String detalhe, OffsetDateTime inicio, boolean jaEstava,
                           boolean avisado) {
        jdbc.update("""
                INSERT INTO ifood_item_pausa (merchant_id, catalog_id, tipo, entidade_id, contexto, nome, categoria,
                    problema, detalhe, inicio, ja_estava, avisado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (merchant_id, catalog_id, tipo, entidade_id) WHERE fim IS NULL
                DO UPDATE SET problema = EXCLUDED.problema, detalhe = EXCLUDED.detalhe
                """, l.merchantId(), l.catalogId(), l.tipo(), l.entidadeId(), l.contexto(), l.nome(), l.categoria(),
                problema, detalhe, ts(inicio), jaEstava, avisado);
    }

    public void atualizarPausa(String merchantId, String chave, String problema, String detalhe) {
        String[] p = chave.split("\\|", 3);
        jdbc.update("""
                UPDATE ifood_item_pausa SET problema = ?, detalhe = ?
                WHERE merchant_id = ? AND catalog_id = ? AND tipo = ? AND entidade_id = ? AND fim IS NULL
                """, problema, detalhe, merchantId, p[0], p[1], p[2]);
    }

    public Optional<Pausa> fecharPausa(String merchantId, String chave, OffsetDateTime fim, String motivo) {
        String[] p = chave.split("\\|", 3);
        return jdbc.query("""
                UPDATE ifood_item_pausa SET fim = ?, motivo_fim = ?
                WHERE merchant_id = ? AND catalog_id = ? AND tipo = ? AND entidade_id = ? AND fim IS NULL
                RETURNING *
                """, PAUSA, ts(fim), motivo, merchantId, p[0], p[1], p[2]).stream().findFirst();
    }

    /** Pausas novas ainda não avisadas no WhatsApp. */
    public List<Pausa> pausasPendentes(String merchantId) {
        return jdbc.query("SELECT * FROM ifood_item_pausa WHERE merchant_id = ? AND fim IS NULL AND NOT avisado "
                + "AND NOT ja_estava ORDER BY inicio, id", PAUSA, merchantId);
    }

    public void marcarAvisadas(Collection<Long> ids) {
        ids.forEach(id -> jdbc.update("UPDATE ifood_item_pausa SET avisado = true WHERE id = ?", id));
    }

    /** O que está parado agora em todas as lojas (para a tela). */
    public List<Map<String, Object>> pausadosAgora() {
        return jdbc.queryForList("""
                SELECT p.id, p.merchant_id, COALESCE(m.nome, p.merchant_id) AS loja, p.tipo, p.entidade_id, p.nome,
                       p.categoria, p.contexto, p.problema, p.detalhe, p.inicio
                FROM ifood_item_pausa p
                LEFT JOIN ifood_merchant m ON m.merchant_id = p.merchant_id
                WHERE p.fim IS NULL
                ORDER BY loja, p.inicio
                """);
    }

    // ------------------------------------------------------------- alterações pela tela

    private static final RowMapper<Alteracao> ALTERACAO = (rs, n) -> new Alteracao(rs.getLong("id"),
            rs.getString("merchant_id"), rs.getString("tipo"), rs.getString("entidade_id"), rs.getString("nome"),
            rs.getString("campo"), rs.getString("antes"), rs.getString("depois"), rs.getString("usuario"),
            odt(rs.getTimestamp("feito_em")), rs.getString("situacao"), rs.getString("erro"),
            odt(rs.getTimestamp("desfeita_em")), rs.getString("desfeita_por"),
            (Long) rs.getObject("desfaz_id"), (Long) rs.getObject("lote_id"));

    public long registrarAlteracao(String merchantId, String tipo, String entidadeId, String nome, String campo,
                                   String antes, String depois, String usuario, OffsetDateTime feitoEm,
                                   String situacao, String erro, Long desfazId) {
        return registrarAlteracao(merchantId, tipo, entidadeId, nome, campo, antes, depois, usuario, feitoEm, situacao,
                erro, desfazId, null);
    }

    public long registrarAlteracao(String merchantId, String tipo, String entidadeId, String nome, String campo,
                                   String antes, String depois, String usuario, OffsetDateTime feitoEm,
                                   String situacao, String erro, Long desfazId, Long loteId) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO cardapio_alteracao (merchant_id, tipo, entidade_id, nome, campo, antes, depois, usuario,
                        feito_em, situacao, erro, desfaz_id, lote_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, merchantId);
            ps.setString(2, tipo);
            ps.setString(3, entidadeId);
            ps.setString(4, nome);
            ps.setString(5, campo);
            ps.setString(6, antes);
            ps.setString(7, depois);
            ps.setString(8, usuario);
            ps.setTimestamp(9, ts(feitoEm));
            ps.setString(10, situacao);
            ps.setString(11, erro);
            ps.setObject(12, desfazId);
            ps.setObject(13, loteId);
            return ps;
        }, kh);
        Map<String, Object> keys = kh.getKeys();
        return ((Number) (keys != null && keys.containsKey("id") ? keys.get("id") : kh.getKey())).longValue();
    }

    public Optional<Alteracao> alteracao(long id) {
        return jdbc.query("SELECT * FROM cardapio_alteracao WHERE id = ?", ALTERACAO, id).stream().findFirst();
    }

    public List<Alteracao> alteracoes(String merchantId, int limite) {
        return jdbc.query("SELECT * FROM cardapio_alteracao WHERE merchant_id = ? ORDER BY feito_em DESC, id DESC LIMIT ?",
                ALTERACAO, merchantId, limite);
    }

    public void marcarDesfeita(long id, String usuario, OffsetDateTime quando) {
        jdbc.update("UPDATE cardapio_alteracao SET situacao = 'DESFEITA', desfeita_em = ?, desfeita_por = ? WHERE id = ?",
                ts(quando), usuario, id);
    }

    /** Só para testes. */
    public void limparTudo() {
        jdbc.execute("TRUNCATE ifood_cardapio, ifood_item_estado, ifood_item_pausa, cardapio_alteracao RESTART IDENTITY");
    }

    // ------------------------------------------------------------- apoio

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Cardapio ler(String json) {
        try {
            return mapper.readValue(json, Cardapio.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cardápio salvo ilegível: " + e.getMessage(), e);
        }
    }

    static Timestamp ts(OffsetDateTime t) {
        return t == null ? null : Timestamp.from(t.toInstant());
    }

    static OffsetDateTime odt(Timestamp t) {
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }
}
