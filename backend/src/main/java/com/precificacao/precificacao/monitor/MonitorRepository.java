package com.precificacao.precificacao.monitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.monitor.MonitorModelos.Chave;
import com.precificacao.precificacao.monitor.MonitorModelos.Incidente;
import com.precificacao.precificacao.monitor.MonitorModelos.IncidenteRelatorio;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorModelos.MotivoSalvo;
import com.precificacao.precificacao.monitor.MonitorModelos.StatusSalvo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Gravação e leitura do monitor (SQL direto, com JSONB para os motivos). */
@Repository
public class MonitorRepository {

    private static final TypeReference<List<MotivoSalvo>> LISTA_MOTIVOS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MonitorRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------- lojas

    public void salvarLojas(List<LojaMerchant> lojas) {
        for (LojaMerchant l : lojas) {
            jdbc.update("""
                    INSERT INTO ifood_merchant (merchant_id, nome, razao_social, ativo, atualizado_em)
                    VALUES (?, ?, ?, true, now())
                    ON CONFLICT (merchant_id) DO UPDATE
                    SET nome = EXCLUDED.nome, razao_social = EXCLUDED.razao_social, ativo = true, atualizado_em = now()
                    """, l.merchantId(), l.nome(), l.razaoSocial());
        }
        if (!lojas.isEmpty()) {
            String[] ids = lojas.stream().map(LojaMerchant::merchantId).toArray(String[]::new);
            jdbc.update(con -> {
                var ps = con.prepareStatement("UPDATE ifood_merchant SET ativo = false WHERE NOT (merchant_id = ANY(?))");
                ps.setArray(1, con.createArrayOf("varchar", ids));
                return ps;
            });
        }
    }

    public Map<String, String> nomesDasLojas() {
        Map<String, String> out = new HashMap<>();
        jdbc.query("SELECT merchant_id, nome FROM ifood_merchant", rs -> {
            String nome = rs.getString("nome");
            out.put(rs.getString("merchant_id"), nome == null || nome.isBlank() ? rs.getString("merchant_id") : nome);
        });
        return out;
    }

    public List<LojaMerchant> lojasAtivas() {
        return jdbc.query("SELECT merchant_id, nome, razao_social FROM ifood_merchant WHERE ativo ORDER BY nome",
                (rs, i) -> new LojaMerchant(rs.getString(1), rs.getString(2), rs.getString(3)));
    }

    // ------------------------------------------------------------- status

    public Map<Chave, StatusSalvo> statusAtual() {
        Map<Chave, StatusSalvo> out = new HashMap<>();
        jdbc.query("SELECT * FROM ifood_status_atual", rs -> {
            List<MotivoSalvo> motivos = lerMotivos(rs.getString("motivos"));
            String assinatura = rs.getString("estado") + "|" + motivos.stream()
                    .map(m -> m.codigo() + ":" + m.estado()).sorted().collect(Collectors.joining(","));
            out.put(new Chave(rs.getString("merchant_id"), rs.getString("operacao"), rs.getString("canal")),
                    new StatusSalvo(rs.getString("estado"), rs.getObject("desde", OffsetDateTime.class), assinatura));
        });
        return out;
    }

    public void salvarVerificacao(String merchantId, StatusOperacao st, OffsetDateTime agora, boolean mudou, OffsetDateTime desde) {
        String motivos = json(MonitorModelos.motivosDe(st));
        jdbc.update("""
                INSERT INTO ifood_status_atual (merchant_id, operacao, canal, estado, motivos, mensagem, desde, verificado_em)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (merchant_id, operacao, canal) DO UPDATE
                SET estado = EXCLUDED.estado, motivos = EXCLUDED.motivos, mensagem = EXCLUDED.mensagem,
                    desde = EXCLUDED.desde, verificado_em = EXCLUDED.verificado_em
                """, merchantId, st.operacao(), st.canal(), st.estado(), motivos, st.mensagem(), desde, agora);
        if (mudou) {
            jdbc.update("""
                    INSERT INTO ifood_status_historico
                      (merchant_id, operacao, canal, estado, estado_ifood, disponivel, motivos, mensagem, reabrivel, bruto, mudou_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?)
                    """, merchantId, st.operacao(), st.canal(), st.estado(), st.estadoIfood(), st.disponivel(), motivos,
                    st.mensagem(), st.reabrivel(), st.bruto() == null ? null : st.bruto().toString(), agora);
        }
    }

    /** Situação atual de todas as lojas, com o nome, para o painel. */
    public List<Map<String, Object>> painel() {
        return jdbc.queryForList("""
                SELECT s.merchant_id, COALESCE(m.nome, s.merchant_id) AS loja, s.operacao, s.canal, s.estado,
                       s.motivos::text AS motivos, s.mensagem, s.desde, s.verificado_em
                FROM ifood_status_atual s
                LEFT JOIN ifood_merchant m ON m.merchant_id = s.merchant_id
                WHERE m.ativo IS DISTINCT FROM false
                ORDER BY CASE s.estado WHEN 'ERROR' THEN 0 WHEN 'PAUSED' THEN 1 WHEN 'WARNING' THEN 2
                                       WHEN 'CLOSED' THEN 3 ELSE 4 END, loja
                """);
    }

    public List<Map<String, Object>> historico(String merchantId, OffsetDateTime de, OffsetDateTime ate) {
        return jdbc.queryForList("""
                SELECT mudou_em, operacao, canal, estado, estado_ifood, motivos::text AS motivos, mensagem, reabrivel
                FROM ifood_status_historico
                WHERE merchant_id = ? AND mudou_em BETWEEN ? AND ?
                ORDER BY mudou_em
                """, merchantId, de, ate);
    }

    // ------------------------------------------------------------- incidentes

    public List<Incidente> incidentesAbertos() {
        return jdbc.query("SELECT * FROM ifood_incidente WHERE fim IS NULL", this::incidente);
    }

    public Incidente abrirIncidente(String merchantId, StatusOperacao st, OffsetDateTime inicio, OffsetDateTime agora,
                                    Object pausas) {
        return jdbc.queryForObject("""
                INSERT INTO ifood_incidente
                  (merchant_id, operacao, canal, estado, motivos, mensagem, reabrivel, pausas, inicio, confirmado_em, ultimo_lembrete_em)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (merchant_id, operacao, canal) WHERE fim IS NULL
                DO UPDATE SET estado = EXCLUDED.estado
                RETURNING *
                """, this::incidente, merchantId, st.operacao(), st.canal(), st.estado(), json(MonitorModelos.motivosDe(st)),
                st.mensagem(), st.reabrivel(), pausas == null ? null : json(pausas), inicio, agora, agora);
    }

    public void atualizarIncidente(long id, StatusOperacao st) {
        jdbc.update("UPDATE ifood_incidente SET estado = ?, motivos = ?::jsonb, mensagem = ?, reabrivel = ? WHERE id = ?",
                st.estado(), json(MonitorModelos.motivosDe(st)), st.mensagem(), st.reabrivel(), id);
    }

    public void marcarLembrete(long id, OffsetDateTime agora) {
        jdbc.update("UPDATE ifood_incidente SET ultimo_lembrete_em = ? WHERE id = ?", agora, id);
    }

    public void fecharIncidente(long id, OffsetDateTime fim, String estadoFinal) {
        jdbc.update("UPDATE ifood_incidente SET fim = ?, estado_final = ? WHERE id = ?", fim, estadoFinal, id);
    }

    /** Incidentes que se sobrepõem ao período [de, ate). merchantIds nulo = todas as lojas. */
    public List<IncidenteRelatorio> relatorio(OffsetDateTime de, OffsetDateTime ate, Collection<String> merchantIds,
                                              OffsetDateTime agora) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.*, COALESCE(m.nome, i.merchant_id) AS loja
                FROM ifood_incidente i
                LEFT JOIN ifood_merchant m ON m.merchant_id = i.merchant_id
                WHERE i.inicio < ? AND COALESCE(i.fim, ?) >= ?
                """);
        List<Object> args = new ArrayList<>(List.of(ate, agora, de));
        if (merchantIds != null) {
            if (merchantIds.isEmpty()) {
                return List.of();
            }
            sql.append(" AND i.merchant_id IN (")
                    .append(merchantIds.stream().map(x -> "?").collect(Collectors.joining(",")))
                    .append(")");
            args.addAll(merchantIds);
        }
        sql.append(" ORDER BY i.inicio");
        return jdbc.query(sql.toString(), (rs, n) -> {
            Incidente i = incidente(rs, n);
            OffsetDateTime fim = i.fim == null ? agora : i.fim;
            double minutos = Math.max(0, java.time.Duration.between(i.inicio, fim).toSeconds() / 60.0);
            return new IncidenteRelatorio(i.id, i.merchantId, rs.getString("loja"), i.operacao, i.canal, i.estado,
                    i.motivos, i.mensagem, i.reabrivel, i.inicio, i.fim, i.estadoFinal, minutos, i.fim == null);
        }, args.toArray());
    }

    // ------------------------------------------------------------- WhatsApp

    public void registrarEnvio(String tipo, String grupo, String chatId, List<Long> incidentes, String corpo,
                               String situacao, String erro, OffsetDateTime quando) {
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO whatsapp_envio (tipo, grupo, chat_id, incidentes, corpo, situacao, erro, enviado_em)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            ps.setString(1, tipo);
            ps.setString(2, grupo);
            ps.setString(3, chatId);
            Array arr = con.createArrayOf("bigint", incidentes == null ? new Long[0] : incidentes.toArray(new Long[0]));
            ps.setArray(4, arr);
            ps.setString(5, corpo);
            ps.setString(6, situacao);
            ps.setString(7, erro);
            ps.setObject(8, quando);
            return ps;
        });
    }

    public OffsetDateTime ultimoEnvio(String tipo) {
        return jdbc.queryForObject(
                "SELECT max(enviado_em) FROM whatsapp_envio WHERE tipo = ? AND situacao IN ('ENVIADO', 'SOMBRA')",
                (rs, i) -> rs.getObject(1, OffsetDateTime.class), tipo);
    }

    public List<Map<String, Object>> envios(int limite) {
        return jdbc.queryForList("""
                SELECT id, tipo, grupo, corpo, situacao, erro, enviado_em
                FROM whatsapp_envio ORDER BY id DESC LIMIT ?
                """, limite);
    }

    // ------------------------------------------------------------- apoio

    private Incidente incidente(ResultSet rs, int n) throws SQLException {
        Incidente i = new Incidente();
        i.id = rs.getLong("id");
        i.merchantId = rs.getString("merchant_id");
        i.operacao = rs.getString("operacao");
        i.canal = rs.getString("canal");
        i.estado = rs.getString("estado");
        i.motivos = lerMotivos(rs.getString("motivos"));
        i.mensagem = rs.getString("mensagem");
        i.reabrivel = (Boolean) rs.getObject("reabrivel");
        i.inicio = rs.getObject("inicio", OffsetDateTime.class);
        i.confirmadoEm = rs.getObject("confirmado_em", OffsetDateTime.class);
        i.fim = rs.getObject("fim", OffsetDateTime.class);
        i.estadoFinal = rs.getString("estado_final");
        i.ultimoLembreteEm = rs.getObject("ultimo_lembrete_em", OffsetDateTime.class);
        return i;
    }

    public List<MotivoSalvo> lerMotivos(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(mapper.readValue(json, LISTA_MOTIVOS));
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /** Apaga os dados do monitor (usado só nos testes). */
    public void limparTudo() {
        jdbc.execute("TRUNCATE ifood_merchant, ifood_status_atual, ifood_status_historico, ifood_incidente, "
                + "whatsapp_envio, whatsapp_grupo_loja, whatsapp_grupo RESTART IDENTITY CASCADE");
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
