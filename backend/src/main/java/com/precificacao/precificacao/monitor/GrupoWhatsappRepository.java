package com.precificacao.precificacao.monitor;

import com.precificacao.precificacao.monitor.MonitorModelos.Grupo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Mapa grupo de WhatsApp → lojas. Substitui a seção "grupos" do config.yaml do monitor em Python. */
@Repository
public class GrupoWhatsappRepository {

    private final JdbcTemplate jdbc;

    public GrupoWhatsappRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Grupo> listar(boolean somenteAtivos) {
        Map<Long, Set<String>> lojas = new HashMap<>();
        jdbc.query("SELECT grupo_id, merchant_id FROM whatsapp_grupo_loja", rs -> {
            lojas.computeIfAbsent(rs.getLong(1), k -> new LinkedHashSet<>()).add(rs.getString(2));
        });
        String sql = "SELECT * FROM whatsapp_grupo" + (somenteAtivos ? " WHERE ativo" : "") + " ORDER BY admin DESC, nome";
        return jdbc.query(sql, (rs, i) -> new Grupo(
                rs.getLong("id"), rs.getString("nome"), rs.getString("chat_id"),
                rs.getBoolean("todas_lojas"), rs.getBoolean("admin"), rs.getBoolean("avisar_retorno"),
                rs.getBoolean("ativo"), lojas.getOrDefault(rs.getLong("id"), Set.of())));
    }

    public Optional<Grupo> buscar(long id) {
        return listar(false).stream().filter(g -> g.id() == id).findFirst();
    }

    /** Grupos que recebem os alertas da loja; sem nenhum, vão para o(s) grupo(s) admin. */
    public static List<Grupo> gruposDaLoja(List<Grupo> grupos, String merchantId) {
        List<Grupo> out = new ArrayList<>();
        for (Grupo g : grupos) {
            if (g.ativo() && g.acompanha(merchantId)) {
                out.add(g);
            }
        }
        if (out.isEmpty()) {
            grupos.stream().filter(g -> g.ativo() && g.admin()).forEach(out::add);
        }
        return out;
    }

    @Transactional
    public long salvar(Long id, String nome, String chatId, boolean todasLojas, boolean admin, boolean avisarRetorno,
                       boolean ativo, Collection<String> merchantIds) {
        long gid;
        if (id == null) {
            gid = jdbc.queryForObject("""
                    INSERT INTO whatsapp_grupo (nome, chat_id, todas_lojas, admin, avisar_retorno, ativo)
                    VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, nome, chatId, todasLojas, admin, avisarRetorno, ativo);
        } else {
            gid = id;
            jdbc.update("""
                    UPDATE whatsapp_grupo SET nome = ?, chat_id = ?, todas_lojas = ?, admin = ?, avisar_retorno = ?, ativo = ?
                    WHERE id = ?
                    """, nome, chatId, todasLojas, admin, avisarRetorno, ativo, gid);
            jdbc.update("DELETE FROM whatsapp_grupo_loja WHERE grupo_id = ?", gid);
        }
        if (merchantIds != null) {
            for (String m : new LinkedHashSet<>(merchantIds)) {
                if (m != null && !m.isBlank()) {
                    jdbc.update("INSERT INTO whatsapp_grupo_loja (grupo_id, merchant_id) VALUES (?, ?)", gid, m.trim());
                }
            }
        }
        return gid;
    }

    public void remover(long id) {
        jdbc.update("DELETE FROM whatsapp_grupo WHERE id = ?", id);
    }
}
