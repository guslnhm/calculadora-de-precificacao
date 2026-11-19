package com.precificacao.precificacao.service;

import com.precificacao.precificacao.acesso.AcessoException;
import com.precificacao.precificacao.acesso.AcessoRepository;
import com.precificacao.precificacao.acesso.AcessoRepository.UsuarioLinha;
import com.precificacao.precificacao.acesso.Sessao;
import com.precificacao.precificacao.dto.LoginRequestDTO;
import com.precificacao.precificacao.dto.LoginResponseDTO;
import com.precificacao.precificacao.exception.CredenciaisInvalidasException;
import com.precificacao.precificacao.ifood.IfoodProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login e sessões.
 *
 * - Senhas com BCrypt. Senhas antigas (texto puro) ainda entram e são convertidas no mesmo login;
 *   se eram curtas, o usuário é obrigado a trocar.
 * - A sessão fica no banco (só o hash do token), então continua valendo depois de um deploy.
 *   Vale 30 dias desde o último uso.
 * - 5 senhas erradas seguidas para o mesmo e-mail bloqueiam por 10 minutos.
 */
@Service
public class AuthService {

    static final Duration VALIDADE_SESSAO = Duration.ofDays(30);
    static final int MAX_FALHAS = 5;
    static final Duration BLOQUEIO = Duration.ofMinutes(10);
    static final Duration INTERVALO_USO = Duration.ofMinutes(1);
    public static final int SENHA_MINIMA = 8;

    private final AcessoRepository repo;
    private final Clock clock;
    private final ZoneId zona;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    private record Falhas(int quantas, OffsetDateTime bloqueadoAte) {
    }

    private final Map<String, Falhas> falhas = new ConcurrentHashMap<>();
    /** Última vez que gravamos o uso de cada usuário (para não escrever no banco a cada requisição). */
    private final Map<Long, OffsetDateTime> ultimoRegistro = new ConcurrentHashMap<>();

    public AuthService(AcessoRepository repo, Clock clock, IfoodProperties ifood) {
        this.repo = repo;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    // ----------------------------------------------------------------- login

    public LoginResponseDTO login(LoginRequestDTO dto) {
        String email = dto.getEmail() == null ? "" : dto.getEmail().trim().toLowerCase();
        OffsetDateTime agora = agora();
        Falhas f = falhas.get(email);
        if (f != null && f.bloqueadoAte() != null && f.bloqueadoAte().isAfter(agora)) {
            long min = Math.max(1, Duration.between(agora, f.bloqueadoAte()).toMinutes() + 1);
            throw new AcessoException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas com senha errada. Tente de novo em " + min + " minuto(s).");
        }
        Optional<UsuarioLinha> u = repo.usuarioPorEmail(email);
        if (u.isEmpty() || !u.get().ativo() || !confere(u.get(), dto.getSenha())) {
            falhou(email, agora);
            throw new CredenciaisInvalidasException();
        }
        falhas.remove(email);
        return entrar(u.get().id());
    }

    /** Abre uma sessão para o usuário (login, ou logo depois do cadastro pelo convite). */
    public LoginResponseDTO entrar(long usuarioId) {
        UsuarioLinha u = repo.usuario(usuarioId).orElseThrow(CredenciaisInvalidasException::new);
        OffsetDateTime agora = agora();
        String token = novoToken();
        repo.criarSessao(hash(token), u.id(), agora, agora.plus(VALIDADE_SESSAO));
        repo.registrarLogin(u.id(), agora);
        repo.registrarUso(u.id(), hoje(), agora);
        ultimoRegistro.put(u.id(), agora);
        return new LoginResponseDTO(token, u.nome(), u.perfil(), new ArrayList<>(repo.lojasDo(u.id())), u.trocarSenha());
    }

    private boolean confere(UsuarioLinha u, String senha) {
        if (senha == null || u.senhaHash() == null) {
            return false;
        }
        if (u.senhaHash().startsWith("$2")) {
            return senha.getBytes(StandardCharsets.UTF_8).length <= 72 && bcrypt.matches(senha, u.senhaHash());
        }
        // senha antiga, em texto puro: confere e já converte
        boolean ok = MessageDigest.isEqual(u.senhaHash().getBytes(StandardCharsets.UTF_8),
                senha.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            repo.trocarSenha(u.id(), bcrypt.encode(senha), senha.length() < SENHA_MINIMA, agoraLocal());
        }
        return ok;
    }

    private void falhou(String email, OffsetDateTime agora) {
        falhas.compute(email, (k, f) -> {
            int n = (f == null || (f.bloqueadoAte() != null && !f.bloqueadoAte().isAfter(agora)) ? 0 : f.quantas()) + 1;
            return new Falhas(n, n >= MAX_FALHAS ? agora.plus(BLOQUEIO) : null);
        });
    }

    // ----------------------------------------------------------------- sessão

    /** Sessão do token, ou null se o token não vale (não existe, venceu ou o usuário foi desativado). */
    public Sessao sessao(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String h = hash(token.startsWith("Bearer ") ? token.substring(7) : token);
        var s = repo.sessao(h).orElse(null);
        OffsetDateTime agora = agora();
        if (s == null || s.expiraEm().isBefore(agora)) {
            return null;
        }
        UsuarioLinha u = repo.usuario(s.usuarioId()).orElse(null);
        if (u == null || !u.ativo()) {
            return null;
        }
        Set<String> lojas = Sessao.MENTORADO.equalsIgnoreCase(u.perfil()) ? repo.lojasDo(u.id()) : Set.of();
        OffsetDateTime ultimo = ultimoRegistro.get(u.id());
        if (ultimo == null || ultimo.plus(INTERVALO_USO).isBefore(agora)) {
            ultimoRegistro.put(u.id(), agora);
            repo.registrarUso(u.id(), hoje(), agora);
            repo.renovarSessao(h, agora, agora.plus(VALIDADE_SESSAO));
        }
        return new Sessao(u.id(), u.nome() == null ? u.email() : u.nome(), u.email(), u.perfil(), lojas,
                u.trocarSenha(), h);
    }

    /** Apaga sessões vencidas uma vez por dia. */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 17 4 * * *")
    public void limparSessoes() {
        repo.limparSessoesVencidas(agora());
    }

    public void sair(Sessao s) {
        if (s != null) {
            repo.apagarSessao(s.tokenHash());
        }
    }

    /** Troca a senha de quem está logado (pede a atual) e derruba as outras sessões dele. */
    public void trocarSenha(Sessao s, String atual, String nova) {
        UsuarioLinha u = repo.usuario(s.usuarioId()).orElseThrow(CredenciaisInvalidasException::new);
        if (!confere(u, atual)) {
            throw AcessoException.invalido("A senha atual não confere");
        }
        validarSenha(nova);
        repo.trocarSenha(u.id(), bcrypt.encode(nova), false, agoraLocal());
        repo.apagarSessoesDo(u.id(), s.tokenHash());
    }

    /** Define a senha sem pedir a atual (link de nova senha) e derruba todas as sessões. */
    public void definirSenha(long usuarioId, String nova) {
        validarSenha(nova);
        repo.trocarSenha(usuarioId, bcrypt.encode(nova), false, agoraLocal());
        repo.apagarSessoesDo(usuarioId, null);
    }

    public String hashSenha(String senha) {
        validarSenha(senha);
        return bcrypt.encode(senha);
    }

    public boolean senhaConfere(long usuarioId, String senha) {
        return repo.usuario(usuarioId).map(u -> confere(u, senha)).orElse(false);
    }

    public static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw AcessoException.invalido("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw AcessoException.invalido("A senha é longa demais (máximo de 72 letras)");
        }
    }

    // ----------------------------------------------------------------- apoio

    /** Token aleatório (32 bytes, base64 para URL). */
    public String novoToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock);
    }

    private LocalDateTime agoraLocal() {
        return LocalDateTime.now(clock);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock.withZone(zona));
    }
}
