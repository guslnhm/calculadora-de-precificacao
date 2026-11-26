package com.precificacao.precificacao.externo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AcessoExternoRepository {

    public record Linha(long id, String nome, String prefixo, boolean ativo, String criadoPor, OffsetDateTime criadoEm,
                        OffsetDateTime ultimoUso, long usos, List<String> lojas) {
    }

    /** Um dia de uma loja, como sai para fora. */
    public record Dia(LocalDate data, String merchantId, String idLojaPortal, String loja, int pedidos, int cancelados,
                      BigDecimal faturamento, BigDecimal faturamentoCancelado, BigDecimal taxaEntrega,
                      BigDecimal descontoLoja, Integer novosClientes, OffsetDateTime atualizadoEm) {
    }

    private final JdbcTemplate jdbc;

    public AcessoExternoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long criar(String nome, String chaveHash, String prefixo, Collection<String> lojas, String criadoPor,
                      OffsetDateTime agora) {
        var kh = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT INTO acesso_externo (nome, chave_hash, prefixo, criado_por, criado_em)
                    VALUES (?, ?, ?, ?, ?)""", new String[]{"id"});
            ps.setString(1, nome);
            ps.setString(2, chaveHash);
            ps.setString(3, prefixo);
            ps.setString(4, criadoPor);
            ps.setObject(5, agora);
            return ps;
        }, kh);
        long id = kh.getKey().longValue();
        definirLojas(id, lojas);
        return id;
    }

    @Transactional
    public void definirLojas(long id, Collection<String> lojas) {
        jdbc.update("DELETE FROM acesso_externo_loja WHERE acesso_id = ?", id);
        for (String m : lojas) {
            jdbc.update("INSERT INTO acesso_externo_loja (acesso_id, merchant_id) VALUES (?, ?)", id, m);
        }
    }

    public boolean renomear(long id, String nome) {
        return jdbc.update("UPDATE acesso_externo SET nome = ? WHERE id = ?", nome, id) > 0;
    }

    public boolean ativar(long id, boolean ativo) {
        return jdbc.update("UPDATE acesso_externo SET ativo = ? WHERE id = ?", ativo, id) > 0;
    }

    public boolean trocarChave(long id, String chaveHash, String prefixo) {
        return jdbc.update("UPDATE acesso_externo SET chave_hash = ?, prefixo = ? WHERE id = ?", chaveHash, prefixo, id) > 0;
    }

    public boolean excluir(long id) {
        return jdbc.update("DELETE FROM acesso_externo WHERE id = ?", id) > 0;
    }

    public List<Linha> listar() {
        Map<Long, List<String>> lojas = new HashMap<>();
        jdbc.query("SELECT acesso_id, merchant_id FROM acesso_externo_loja ORDER BY merchant_id",
                rs -> {
                    lojas.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
                });
        return jdbc.query("SELECT * FROM acesso_externo ORDER BY ativo DESC, criado_em DESC", (rs, i) -> new Linha(
                rs.getLong("id"), rs.getString("nome"), rs.getString("prefixo"), rs.getBoolean("ativo"),
                rs.getString("criado_por"), rs.getObject("criado_em", OffsetDateTime.class),
                rs.getObject("ultimo_uso", OffsetDateTime.class), rs.getLong("usos"),
                lojas.getOrDefault(rs.getLong("id"), List.of())));
    }

    public Optional<Linha> porId(long id) {
        return listar().stream().filter(l -> l.id() == id).findFirst();
    }

    /** Acesso ativo dono da chave (pelo hash) e as lojas dele. */
    public Optional<Linha> ativoPorHash(String chaveHash) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM acesso_externo WHERE chave_hash = ? AND ativo", Long.class, chaveHash);
        return ids.isEmpty() ? Optional.empty() : porId(ids.get(0));
    }

    public void registrarUso(long id, OffsetDateTime agora) {
        jdbc.update("UPDATE acesso_externo SET ultimo_uso = ?, usos = usos + 1 WHERE id = ?", agora, id);
    }

    /** Nome das lojas do iFood (as que têm dados diários no Painel). */
    public Map<String, String> lojasIfood() {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query("SELECT merchant_id, nome FROM ifood_merchant ORDER BY nome", rs -> {
            out.put(rs.getString(1), rs.getString(2));
        });
        return out;
    }

    public List<Dia> dias(Collection<String> lojas, LocalDate de, LocalDate ate) {
        if (lojas.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", java.util.Collections.nCopies(lojas.size(), "?"));
        List<Object> args = new ArrayList<>(lojas);
        args.add(Date.valueOf(de));
        args.add(Date.valueOf(ate));
        return jdbc.query("""
                SELECT d.dia, d.merchant_id, s.short_id, COALESCE(m.nome, d.merchant_id) AS loja, d.pedidos, d.cancelados,
                       d.gmv, d.gmv_cancelado, d.taxa_entrega, d.incentivo_loja, d.novos_clientes, d.coletado_em
                FROM painel_dia d LEFT JOIN ifood_merchant m ON m.merchant_id = d.merchant_id
                LEFT JOIN painel_short_id s ON s.merchant_id = d.merchant_id
                WHERE d.merchant_id IN (%s) AND d.dia BETWEEN ? AND ?
                ORDER BY d.dia, loja""".formatted(in), (rs, i) -> new Dia(rs.getDate("dia").toLocalDate(),
                rs.getString("merchant_id"), rs.getString("short_id"), rs.getString("loja"), rs.getInt("pedidos"),
                rs.getInt("cancelados"), rs.getBigDecimal("gmv"), rs.getBigDecimal("gmv_cancelado"),
                rs.getBigDecimal("taxa_entrega"), rs.getBigDecimal("incentivo_loja"), (Integer) rs.getObject("novos_clientes"),
                rs.getObject("coletado_em", OffsetDateTime.class)), args.toArray());
    }

    /** Só para testes. */
    void limparTudo() {
        jdbc.update("DELETE FROM acesso_externo");
    }
}
