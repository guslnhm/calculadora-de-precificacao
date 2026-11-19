package com.precificacao.precificacao.acesso;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Usuários, sessões, convites e registro de uso. */
@Repository
public class AcessoRepository {

    public record UsuarioLinha(long id, String nome, String email, String senhaHash, String perfil, boolean ativo,
                               LocalDate dataNascimento, boolean trocarSenha) {
    }

    public record ConviteLinha(long id, String token, String descricao, String perfil, List<String> lojas,
                               int maxUsos, int usos, String criadoPor, OffsetDateTime criadoEm,
                               OffsetDateTime expiraEm, OffsetDateTime revogadoEm) {
    }

    public record Uso(long usuarioId, OffsetDateTime ultimoAcesso, int dias7, int dias30, int logins30,
                      int minutos30) {
    }

    private final JdbcTemplate jdbc;

    public AcessoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ----------------------------------------------------------------- usuários

    private static final String COLS_USUARIO =
            "id, nome, email, senha_hash, perfil, ativo, data_nascimento, trocar_senha";

    private static UsuarioLinha usuario(ResultSet rs) throws SQLException {
        Date d = rs.getDate("data_nascimento");
        return new UsuarioLinha(rs.getLong("id"), rs.getString("nome"), rs.getString("email"),
                rs.getString("senha_hash"), rs.getString("perfil"), rs.getBoolean("ativo"),
                d == null ? null : d.toLocalDate(), rs.getBoolean("trocar_senha"));
    }

    public Optional<UsuarioLinha> usuarioPorEmail(String email) {
        return jdbc.query("SELECT " + COLS_USUARIO + " FROM usuarios WHERE lower(email) = lower(?) ORDER BY id LIMIT 1",
                (rs, i) -> usuario(rs), email.trim()).stream().findFirst();
    }

    public Optional<UsuarioLinha> usuario(long id) {
        return jdbc.query("SELECT " + COLS_USUARIO + " FROM usuarios WHERE id = ?", (rs, i) -> usuario(rs), id)
                .stream().findFirst();
    }

    public List<UsuarioLinha> usuarios() {
        return jdbc.query("SELECT " + COLS_USUARIO + " FROM usuarios ORDER BY lower(nome)", (rs, i) -> usuario(rs));
    }

    public long criarUsuario(String nome, String email, String senhaHash, String perfil, LocalDate nascimento,
                             Long conviteId, LocalDateTime agora) {
        return jdbc.queryForObject("""
                INSERT INTO usuarios (nome, email, senha_hash, perfil, ativo, data_nascimento, convite_id,
                                      criado_em, atualizado_em)
                VALUES (?, ?, ?, ?, TRUE, ?, ?, ?, ?) RETURNING id""", Long.class,
                nome, email, senhaHash, perfil, nascimento == null ? null : Date.valueOf(nascimento), conviteId,
                Timestamp.valueOf(agora), Timestamp.valueOf(agora));
    }

    public void trocarSenha(long id, String hash, boolean trocarDepois, LocalDateTime agora) {
        jdbc.update("UPDATE usuarios SET senha_hash = ?, trocar_senha = ?, atualizado_em = ? WHERE id = ?",
                hash, trocarDepois, Timestamp.valueOf(agora), id);
    }

    public void completarNascimento(long id, LocalDate nascimento) {
        jdbc.update("UPDATE usuarios SET data_nascimento = COALESCE(data_nascimento, ?) WHERE id = ?",
                Date.valueOf(nascimento), id);
    }

    public void atualizarUsuario(long id, String nome, String perfil, boolean ativo, LocalDate nascimento,
                                 LocalDateTime agora) {
        jdbc.update("""
                UPDATE usuarios SET nome = ?, perfil = ?, ativo = ?, data_nascimento = ?, atualizado_em = ?
                WHERE id = ?""", nome, perfil, ativo, nascimento == null ? null : Date.valueOf(nascimento),
                Timestamp.valueOf(agora), id);
    }

    public Set<String> lojasDo(long usuarioId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT chave FROM usuario_loja WHERE usuario_id = ? ORDER BY chave", String.class, usuarioId));
    }

    public Map<Long, List<String>> lojasDeTodos() {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        jdbc.query("SELECT usuario_id, chave FROM usuario_loja ORDER BY usuario_id, chave",
                rs -> {
                    out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
                });
        return out;
    }

    public void adicionarLojas(long usuarioId, List<String> chaves) {
        for (String c : chaves) {
            jdbc.update("INSERT INTO usuario_loja (usuario_id, chave) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    usuarioId, c);
        }
    }

    public void definirLojas(long usuarioId, List<String> chaves) {
        jdbc.update("DELETE FROM usuario_loja WHERE usuario_id = ?", usuarioId);
        adicionarLojas(usuarioId, chaves);
    }

    // ----------------------------------------------------------------- sessões

    public record SessaoLinha(long usuarioId, OffsetDateTime expiraEm, OffsetDateTime ultimoUso) {
    }

    public void criarSessao(String tokenHash, long usuarioId, OffsetDateTime agora, OffsetDateTime expira) {
        jdbc.update("INSERT INTO sessao (token_hash, usuario_id, criado_em, expira_em, ultimo_uso_em) VALUES (?, ?, ?, ?, ?)",
                tokenHash, usuarioId, agora, expira, agora);
    }

    public Optional<SessaoLinha> sessao(String tokenHash) {
        return jdbc.query("SELECT usuario_id, expira_em, ultimo_uso_em FROM sessao WHERE token_hash = ?",
                (rs, i) -> new SessaoLinha(rs.getLong(1), rs.getObject(2, OffsetDateTime.class),
                        rs.getObject(3, OffsetDateTime.class)), tokenHash).stream().findFirst();
    }

    public void renovarSessao(String tokenHash, OffsetDateTime agora, OffsetDateTime expira) {
        jdbc.update("UPDATE sessao SET ultimo_uso_em = ?, expira_em = ? WHERE token_hash = ?", agora, expira, tokenHash);
    }

    public void apagarSessao(String tokenHash) {
        jdbc.update("DELETE FROM sessao WHERE token_hash = ?", tokenHash);
    }

    public void apagarSessoesDo(long usuarioId, String excetoHash) {
        jdbc.update("DELETE FROM sessao WHERE usuario_id = ? AND token_hash <> ?", usuarioId,
                excetoHash == null ? "" : excetoHash);
    }

    public void limparSessoesVencidas(OffsetDateTime agora) {
        jdbc.update("DELETE FROM sessao WHERE expira_em < ?", agora);
    }

    // ----------------------------------------------------------------- uso

    public void registrarLogin(long usuarioId, OffsetDateTime agora) {
        jdbc.update("INSERT INTO usuario_login (usuario_id, quando) VALUES (?, ?)", usuarioId, agora);
    }

    /** Marca uso: último acesso do usuário e o dia na tabela de atividade (1 "minuto ativo" por chamada). */
    public void registrarUso(long usuarioId, LocalDate dia, OffsetDateTime agora) {
        jdbc.update("UPDATE usuarios SET ultimo_acesso_em = ? WHERE id = ?", agora, usuarioId);
        jdbc.update("""
                INSERT INTO usuario_atividade (usuario_id, dia, primeiro, ultimo, requisicoes) VALUES (?, ?, ?, ?, 1)
                ON CONFLICT (usuario_id, dia) DO UPDATE SET ultimo = EXCLUDED.ultimo,
                    requisicoes = usuario_atividade.requisicoes + 1""",
                usuarioId, Date.valueOf(dia), agora, agora);
    }

    public Map<Long, Uso> uso(LocalDate hoje, OffsetDateTime agora) {
        Map<Long, Uso> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT u.id, u.ultimo_acesso_em,
                       (SELECT count(*) FROM usuario_atividade a WHERE a.usuario_id = u.id AND a.dia > ?) AS d7,
                       (SELECT count(*) FROM usuario_atividade a WHERE a.usuario_id = u.id AND a.dia > ?) AS d30,
                       (SELECT count(*) FROM usuario_login l WHERE l.usuario_id = u.id AND l.quando > ?) AS l30,
                       (SELECT coalesce(sum(requisicoes), 0) FROM usuario_atividade a
                         WHERE a.usuario_id = u.id AND a.dia > ?) AS m30
                FROM usuarios u""", rs -> {
            out.put(rs.getLong("id"), new Uso(rs.getLong("id"), rs.getObject("ultimo_acesso_em", OffsetDateTime.class),
                    rs.getInt("d7"), rs.getInt("d30"), rs.getInt("l30"), rs.getInt("m30")));
        }, Date.valueOf(hoje.minusDays(7)), Date.valueOf(hoje.minusDays(30)), agora.minusDays(30),
                Date.valueOf(hoje.minusDays(30)));
        return out;
    }

    /** Quantos usuários de cada perfil usaram o site em cada dia (a partir de {@code desde}). */
    public Map<LocalDate, Integer> usuariosPorDia(LocalDate desde, String perfil) {
        Map<LocalDate, Integer> out = new LinkedHashMap<>();
        jdbc.query("""
                SELECT a.dia, count(*) FROM usuario_atividade a JOIN usuarios u ON u.id = a.usuario_id
                WHERE a.dia >= ? AND u.perfil = ? GROUP BY a.dia ORDER BY a.dia""",
                rs -> {
                    out.put(rs.getDate(1).toLocalDate(), rs.getInt(2));
                }, Date.valueOf(desde), perfil);
        return out;
    }

    // ----------------------------------------------------------------- convites

    private static ConviteLinha convite(ResultSet rs) throws SQLException {
        Array a = rs.getArray("lojas");
        List<String> lojas = a == null ? List.of() : Arrays.asList((String[]) a.getArray());
        return new ConviteLinha(rs.getLong("id"), rs.getString("token"), rs.getString("descricao"),
                rs.getString("perfil"), lojas, rs.getInt("max_usos"), rs.getInt("usos"), rs.getString("criado_por"),
                rs.getObject("criado_em", OffsetDateTime.class), rs.getObject("expira_em", OffsetDateTime.class),
                rs.getObject("revogado_em", OffsetDateTime.class));
    }

    public long criarConvite(String token, String descricao, String perfil, List<String> lojas, int maxUsos,
                             String criadoPor, OffsetDateTime agora, OffsetDateTime expira) {
        return jdbc.queryForObject("""
                INSERT INTO convite (token, descricao, perfil, lojas, max_usos, criado_por, criado_em, expira_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class,
                token, descricao, perfil, lojas.toArray(new String[0]), maxUsos, criadoPor, agora, expira);
    }

    public Optional<ConviteLinha> convitePorToken(String token) {
        return jdbc.query("SELECT * FROM convite WHERE token = ?", (rs, i) -> convite(rs), token).stream().findFirst();
    }

    public List<ConviteLinha> convites() {
        return jdbc.query("SELECT * FROM convite ORDER BY criado_em DESC", (rs, i) -> convite(rs));
    }

    /** Conta um uso do convite, se ainda houver vaga. Devolve false se o convite já se esgotou. */
    public boolean usarConvite(long id) {
        return jdbc.update("UPDATE convite SET usos = usos + 1 WHERE id = ? AND usos < max_usos", id) == 1;
    }

    public void revogarConvite(long id, OffsetDateTime agora) {
        jdbc.update("UPDATE convite SET revogado_em = COALESCE(revogado_em, ?) WHERE id = ?", agora, id);
    }

    /** Quem entrou por cada convite (id do convite → nomes). */
    public Map<Long, List<String>> usuariosPorConvite() {
        Map<Long, List<String>> out = new LinkedHashMap<>();
        jdbc.query("SELECT convite_id, nome FROM usuarios WHERE convite_id IS NOT NULL ORDER BY id", rs -> {
            out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
        });
        return out;
    }

    // ----------------------------------------------------------------- link de nova senha

    public void criarLinkSenha(String tokenHash, long usuarioId, OffsetDateTime agora, OffsetDateTime expira) {
        jdbc.update("UPDATE senha_link SET usado_em = ? WHERE usuario_id = ? AND usado_em IS NULL", agora, usuarioId);
        jdbc.update("INSERT INTO senha_link (token_hash, usuario_id, criado_em, expira_em) VALUES (?, ?, ?, ?)",
                tokenHash, usuarioId, agora, expira);
    }

    /** Usuário do link, se o link ainda vale. */
    public Optional<Long> linkSenhaValido(String tokenHash, OffsetDateTime agora) {
        return jdbc.queryForList("""
                SELECT usuario_id FROM senha_link WHERE token_hash = ? AND usado_em IS NULL AND expira_em > ?""",
                Long.class, tokenHash, agora).stream().findFirst();
    }

    public boolean usarLinkSenha(String tokenHash, OffsetDateTime agora) {
        return jdbc.update("UPDATE senha_link SET usado_em = ? WHERE token_hash = ? AND usado_em IS NULL", agora,
                tokenHash) == 1;
    }

    /** Só para testes. */
    void limparTudo() {
        jdbc.update("DELETE FROM senha_link");
        jdbc.update("DELETE FROM usuario_login");
        jdbc.update("DELETE FROM usuario_atividade");
        jdbc.update("DELETE FROM sessao");
        jdbc.update("DELETE FROM usuario_loja");
        jdbc.update("DELETE FROM usuarios");
        jdbc.update("DELETE FROM convite");
    }
}
