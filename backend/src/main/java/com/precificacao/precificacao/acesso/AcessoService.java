package com.precificacao.precificacao.acesso;

import com.precificacao.precificacao.acesso.AcessoRepository.ConviteLinha;
import com.precificacao.precificacao.acesso.AcessoRepository.UsuarioLinha;
import com.precificacao.precificacao.dto.LoginResponseDTO;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.painel.PainelService;
import com.precificacao.precificacao.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Convites por link, cadastro do mentorado, gestão dos usuários e adesão. */
@Service
public class AcessoService {

    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final Duration VALIDADE_LINK_SENHA = Duration.ofHours(48);

    // ----------------------------------------------------------------- formatos da API

    public record NovoConvite(String descricao, List<String> lojas, String perfil, Integer validadeDias,
                              Integer maxUsos) {
    }

    public record LojaNome(String chave, String nome) {
    }

    public record Convite(long id, String token, String descricao, String perfil, List<LojaNome> lojas, int maxUsos,
                          int usos, String criadoPor, OffsetDateTime criadoEm, OffsetDateTime expiraEm,
                          OffsetDateTime revogadoEm, String situacao, List<String> usadoPor) {
    }

    /** O que a página pública do convite mostra antes do cadastro. */
    public record ConvitePublico(boolean valido, String motivo, String descricao, String perfil, List<String> lojas) {
    }

    public record Cadastro(String nome, String email, String senha, LocalDate dataNascimento) {
    }

    public record Usuario(long id, String nome, String email, String perfil, boolean ativo, LocalDate dataNascimento,
                          List<LojaNome> lojas, OffsetDateTime ultimoAcesso, int diasAtivos7, int diasAtivos30,
                          int logins30, int minutos30, boolean aniversarioHoje, Integer diasParaAniversario) {
    }

    public record EdicaoUsuario(String nome, String perfil, Boolean ativo, LocalDate dataNascimento,
                                List<String> lojas) {
    }

    public record DiaUso(LocalDate dia, int mentorados) {
    }

    public record Adesao(int mentorados, int ativos7, int ativos30, int nuncaEntraram, List<DiaUso> dias) {
    }

    private final AcessoRepository repo;
    private final AuthService auth;
    private final PainelService painel;
    private final Clock clock;
    private final ZoneId zona;

    public AcessoService(AcessoRepository repo, AuthService auth, PainelService painel, Clock clock,
                         IfoodProperties ifood) {
        this.repo = repo;
        this.auth = auth;
        this.painel = painel;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    // ----------------------------------------------------------------- convites

    public Convite criarConvite(NovoConvite req, String criadoPor) {
        String perfil = perfil(req.perfil());
        List<String> lojas = lojasValidas(req.lojas());
        if (Sessao.MENTORADO.equals(perfil) && lojas.isEmpty()) {
            throw AcessoException.invalido("Escolha pelo menos uma loja para o convite");
        }
        int dias = req.validadeDias() == null ? 7 : req.validadeDias();
        if (dias < 1 || dias > 90) {
            throw AcessoException.invalido("A validade precisa ser de 1 a 90 dias");
        }
        int usos = req.maxUsos() == null ? 1 : req.maxUsos();
        if (usos < 1 || usos > 50) {
            throw AcessoException.invalido("O link pode ser usado de 1 a 50 vezes");
        }
        OffsetDateTime agora = agora();
        String descricao = req.descricao() == null || req.descricao().isBlank() ? null : req.descricao().trim();
        repo.criarConvite(auth.novoToken(), descricao, perfil, lojas, usos, criadoPor, agora, agora.plusDays(dias));
        return convites().get(0);
    }

    public List<Convite> convites() {
        Map<String, String> nomes = nomesLojas();
        Map<Long, List<String>> usados = repo.usuariosPorConvite();
        List<Convite> out = new ArrayList<>();
        for (ConviteLinha c : repo.convites()) {
            out.add(new Convite(c.id(), c.token(), c.descricao(), c.perfil(), nomear(c.lojas(), nomes), c.maxUsos(),
                    c.usos(), c.criadoPor(), c.criadoEm(), c.expiraEm(), c.revogadoEm(), situacao(c),
                    usados.getOrDefault(c.id(), List.of())));
        }
        return out;
    }

    public void revogarConvite(long id) {
        repo.revogarConvite(id, agora());
    }

    public ConvitePublico conviteParaCadastro(String token) {
        ConviteLinha c = repo.convitePorToken(token).orElse(null);
        if (c == null) {
            return new ConvitePublico(false, "Este link de acesso não existe. Confira se copiou o link inteiro.",
                    null, null, List.of());
        }
        String s = situacao(c);
        Map<String, String> nomes = nomesLojas();
        List<String> lojas = c.lojas().stream().map(k -> nomes.getOrDefault(k, k)).toList();
        return new ConvitePublico("ATIVO".equals(s), motivo(s), c.descricao(), c.perfil(), lojas);
    }

    /**
     * Cadastro pelo convite. Se o e-mail já tem acesso, a pessoa entra com a senha que já tem e ganha as lojas
     * do convite.
     */
    @Transactional
    public LoginResponseDTO aceitar(String token, Cadastro c) {
        ConviteLinha conv = repo.convitePorToken(token)
                .orElseThrow(() -> new AcessoException(HttpStatus.NOT_FOUND, "Este link de acesso não existe"));
        String s = situacao(conv);
        if (!"ATIVO".equals(s)) {
            throw new AcessoException(HttpStatus.GONE, motivo(s));
        }
        String email = c.email() == null ? "" : c.email().trim().toLowerCase();
        if (!EMAIL.matcher(email).matches() || email.length() > 180) {
            throw AcessoException.invalido("Informe um e-mail válido");
        }
        validarNascimento(c.dataNascimento());
        Optional<UsuarioLinha> existente = repo.usuarioPorEmail(email);
        long id;
        if (existente.isPresent()) {
            UsuarioLinha u = existente.get();
            if (!u.ativo()) {
                throw new AcessoException(HttpStatus.FORBIDDEN,
                        "Esse e-mail está com o acesso desativado. Fale com a sua mentoria.");
            }
            if (!auth.senhaConfere(u.id(), c.senha())) {
                throw new AcessoException(HttpStatus.CONFLICT,
                        "Esse e-mail já tem acesso. Use a senha que você já cadastrou para juntar as lojas novas.");
            }
            id = u.id();
            repo.completarNascimento(id, c.dataNascimento());
        } else {
            String nome = c.nome() == null ? "" : c.nome().trim();
            if (nome.length() < 2 || nome.length() > 120) {
                throw AcessoException.invalido("Informe o seu nome");
            }
            String hash = auth.hashSenha(c.senha());
            if (!repo.usarConvite(conv.id())) {
                throw new AcessoException(HttpStatus.GONE, motivo("ESGOTADO"));
            }
            id = repo.criarUsuario(nome, email, hash, conv.perfil(), c.dataNascimento(), conv.id(), agoraLocal());
            repo.adicionarLojas(id, conv.lojas());
            return auth.entrar(id);
        }
        if (!repo.usarConvite(conv.id())) {
            throw new AcessoException(HttpStatus.GONE, motivo("ESGOTADO"));
        }
        repo.adicionarLojas(id, conv.lojas());
        return auth.entrar(id);
    }

    // ----------------------------------------------------------------- usuários

    public List<Usuario> usuarios() {
        Map<String, String> nomes = nomesLojas();
        Map<Long, List<String>> lojas = repo.lojasDeTodos();
        LocalDate hoje = hoje();
        Map<Long, AcessoRepository.Uso> uso = repo.uso(hoje, agora());
        List<Usuario> out = new ArrayList<>();
        for (UsuarioLinha u : repo.usuarios()) {
            AcessoRepository.Uso x = uso.get(u.id());
            Integer dias = diasParaAniversario(u.dataNascimento(), hoje);
            out.add(new Usuario(u.id(), u.nome(), u.email(), u.perfil(), u.ativo(), u.dataNascimento(),
                    nomear(lojas.getOrDefault(u.id(), List.of()), nomes),
                    x == null ? null : x.ultimoAcesso(), x == null ? 0 : x.dias7(), x == null ? 0 : x.dias30(),
                    x == null ? 0 : x.logins30(), x == null ? 0 : x.minutos30(),
                    dias != null && dias == 0, dias));
        }
        return out;
    }

    @Transactional
    public void editarUsuario(long id, EdicaoUsuario req, Sessao quem) {
        UsuarioLinha u = repo.usuario(id).orElseThrow(() -> new AcessoException(HttpStatus.NOT_FOUND,
                "Usuário não encontrado"));
        String nome = req.nome() == null || req.nome().isBlank() ? u.nome() : req.nome().trim();
        String perfil = req.perfil() == null ? u.perfil() : perfil(req.perfil());
        if (Sessao.EQUIPE.equals(perfil) && !Sessao.MENTORADO.equalsIgnoreCase(u.perfil())) {
            perfil = u.perfil(); // mantém ADMIN etc.
        }
        boolean ativo = req.ativo() == null ? u.ativo() : req.ativo();
        if (quem != null && quem.usuarioId() == id && (!ativo || Sessao.MENTORADO.equals(perfil))) {
            throw AcessoException.invalido("Você não pode desativar nem limitar o seu próprio acesso");
        }
        LocalDate nasc = req.dataNascimento() == null ? u.dataNascimento() : req.dataNascimento();
        if (req.dataNascimento() != null) {
            validarNascimento(nasc);
        }
        repo.atualizarUsuario(id, nome, perfil, ativo, nasc, agoraLocal());
        if (req.lojas() != null) {
            repo.definirLojas(id, lojasValidas(req.lojas()));
        }
        if (!ativo) {
            repo.apagarSessoesDo(id, null);
        }
    }

    /** Link para a pessoa criar uma senha nova (vale 48 h e uma vez só). Devolve o token. */
    public String gerarLinkSenha(long usuarioId) {
        repo.usuario(usuarioId).orElseThrow(() -> new AcessoException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        String token = auth.novoToken();
        OffsetDateTime agora = agora();
        repo.criarLinkSenha(AuthService.hash(token), usuarioId, agora, agora.plus(VALIDADE_LINK_SENHA));
        return token;
    }

    public Map<String, Object> linkSenha(String token) {
        Optional<Long> id = repo.linkSenhaValido(AuthService.hash(token), agora());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valido", id.isPresent());
        id.flatMap(repo::usuario).ifPresent(u -> {
            m.put("nome", u.nome());
            m.put("email", u.email());
        });
        if (id.isEmpty()) {
            m.put("motivo", "Este link já foi usado ou venceu. Peça um link novo para a sua mentoria.");
        }
        return m;
    }

    @Transactional
    public LoginResponseDTO usarLinkSenha(String token, String senha) {
        String h = AuthService.hash(token);
        long id = repo.linkSenhaValido(h, agora()).orElseThrow(() -> new AcessoException(HttpStatus.GONE,
                "Este link já foi usado ou venceu. Peça um link novo para a sua mentoria."));
        AuthService.validarSenha(senha);
        UsuarioLinha u = repo.usuario(id).orElseThrow();
        if (!u.ativo()) {
            throw new AcessoException(HttpStatus.FORBIDDEN, "Esse acesso está desativado. Fale com a sua mentoria.");
        }
        if (!repo.usarLinkSenha(h, agora())) {
            throw new AcessoException(HttpStatus.GONE, "Este link já foi usado");
        }
        auth.definirSenha(id, senha);
        return auth.entrar(id);
    }

    // ----------------------------------------------------------------- adesão

    public Adesao adesao() {
        LocalDate hoje = hoje();
        List<Usuario> us = usuarios().stream()
                .filter(u -> u.ativo() && Sessao.MENTORADO.equalsIgnoreCase(u.perfil())).toList();
        int a7 = (int) us.stream().filter(u -> u.diasAtivos7() > 0).count();
        int a30 = (int) us.stream().filter(u -> u.diasAtivos30() > 0).count();
        int nunca = (int) us.stream().filter(u -> u.ultimoAcesso() == null).count();
        LocalDate desde = hoje.minusDays(29);
        Map<LocalDate, Integer> porDia = repo.usuariosPorDia(desde, Sessao.MENTORADO);
        List<DiaUso> dias = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hoje); d = d.plusDays(1)) {
            dias.add(new DiaUso(d, porDia.getOrDefault(d, 0)));
        }
        return new Adesao(us.size(), a7, a30, nunca, dias);
    }

    // ----------------------------------------------------------------- apoio

    static Integer diasParaAniversario(LocalDate nascimento, LocalDate hoje) {
        if (nascimento == null) {
            return null;
        }
        MonthDay md = MonthDay.from(nascimento);
        LocalDate prox = md.atYear(hoje.getYear());
        if (prox.isBefore(hoje)) {
            prox = md.atYear(hoje.getYear() + 1);
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(hoje, prox);
    }

    private void validarNascimento(LocalDate d) {
        if (d == null) {
            throw AcessoException.invalido("Informe a data de nascimento");
        }
        LocalDate hoje = hoje();
        if (d.isAfter(hoje.minusYears(12)) || d.isBefore(hoje.minusYears(110))) {
            throw AcessoException.invalido("Confira a data de nascimento");
        }
    }

    private String situacao(ConviteLinha c) {
        if (c.revogadoEm() != null) {
            return "REVOGADO";
        }
        if (c.usos() >= c.maxUsos()) {
            return "ESGOTADO";
        }
        if (c.expiraEm().isBefore(agora())) {
            return "VENCIDO";
        }
        return "ATIVO";
    }

    private static String motivo(String situacao) {
        return switch (situacao) {
            case "REVOGADO" -> "Este link de acesso foi cancelado. Peça um link novo para a sua mentoria.";
            case "ESGOTADO" -> "Este link de acesso já foi usado. Se você já se cadastrou, é só entrar com o seu e-mail.";
            case "VENCIDO" -> "Este link de acesso venceu. Peça um link novo para a sua mentoria.";
            default -> null;
        };
    }

    private static String perfil(String p) {
        if (p == null || p.isBlank() || Sessao.MENTORADO.equalsIgnoreCase(p)) {
            return Sessao.MENTORADO;
        }
        if (Sessao.EQUIPE.equalsIgnoreCase(p)) {
            return Sessao.EQUIPE;
        }
        throw AcessoException.invalido("Perfil inválido");
    }

    private List<String> lojasValidas(List<String> lojas) {
        if (lojas == null) {
            return List.of();
        }
        Map<String, String> nomes = nomesLojas();
        List<String> out = new ArrayList<>(new LinkedHashSet<>(lojas));
        for (String l : out) {
            if (!nomes.containsKey(l)) {
                throw AcessoException.invalido("Loja não encontrada: " + l);
            }
        }
        return out;
    }

    private Map<String, String> nomesLojas() {
        Map<String, String> m = new LinkedHashMap<>();
        for (PainelService.OpcaoLoja o : painel.opcoesLojas()) {
            m.put(o.chave(), o.nome());
        }
        return m;
    }

    private static List<LojaNome> nomear(List<String> chaves, Map<String, String> nomes) {
        return chaves.stream().map(k -> new LojaNome(k, nomes.getOrDefault(k, k))).toList();
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
