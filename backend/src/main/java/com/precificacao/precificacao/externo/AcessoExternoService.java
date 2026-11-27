package com.precificacao.precificacao.externo;

import com.precificacao.precificacao.acesso.AcessoException;
import com.precificacao.precificacao.externo.AcessoExternoRepository.Dia;
import com.precificacao.precificacao.externo.AcessoExternoRepository.Linha;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Acessos de fora (o BI de metas de um parceiro, por exemplo) aos números diários do Painel.
 * Cada acesso tem uma chave própria e só enxerga as lojas marcadas. A chave do iFood nunca sai daqui:
 * quem chama recebe o que o Painel já coletou (com a releitura dos dias que o iFood completa depois).
 */
@Service
public class AcessoExternoService {

    /** Começo da chave, para saber que é nossa e reconhecer na tela. */
    static final String PREFIXO = "rzq_";
    static final int MAX_DIAS = 400;

    public record Acesso(long id, String nome, String inicioChave, boolean ativo, String criadoPor,
                         OffsetDateTime criadoEm, OffsetDateTime ultimoUso, long usos, List<Loja> lojas) {
    }

    public record Loja(String merchantId, String nome) {
    }

    /** Resposta da criação / troca de chave: a chave só aparece aqui, uma vez. */
    public record ComChave(Acesso acesso, String chave) {
    }

    public record Pedido(String nome, List<String> lojas) {
    }

    public record Edicao(String nome, List<String> lojas, Boolean ativo) {
    }

    /** Um dia de uma loja na resposta para fora. */
    public record Venda(LocalDate data, String merchantId, String loja, int pedidos, int cancelados,
                        BigDecimal faturamento, BigDecimal faturamentoCancelado, BigDecimal ticketMedio,
                        Integer novosClientes, OffsetDateTime atualizadoEm, String idLojaPortal,
                        BigDecimal valorItens, BigDecimal taxaEntrega, BigDecimal descontoLoja,
                        Integer canceladosPagos, BigDecimal valorItensCanceladosPagos) {
    }

    private final AcessoExternoRepository repo;
    private final ItensDoRelatorio relatorio;
    private final AuthService auth;
    private final Clock clock;
    private final ZoneId zona;

    public AcessoExternoService(AcessoExternoRepository repo, ItensDoRelatorio relatorio, AuthService auth, Clock clock,
                                IfoodProperties ifood) {
        this.repo = repo;
        this.relatorio = relatorio;
        this.auth = auth;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    // ------------------------------------------------------------------ gestão (equipe)

    public List<Loja> lojasDisponiveis() {
        return repo.lojasIfood().entrySet().stream().map(e -> new Loja(e.getKey(), e.getValue())).toList();
    }

    public List<Acesso> listar() {
        Map<String, String> nomes = repo.lojasIfood();
        return repo.listar().stream().map(l -> acesso(l, nomes)).toList();
    }

    public ComChave criar(Pedido p, String criadoPor) {
        String nome = nome(p.nome());
        List<String> lojas = lojasValidas(p.lojas());
        String chave = novaChave();
        long id = repo.criar(nome, AuthService.hash(chave), inicio(chave), lojas, criadoPor, OffsetDateTime.now(clock));
        return new ComChave(buscar(id), chave);
    }

    public Acesso editar(long id, Edicao e) {
        buscar(id);
        if (e.nome() != null) {
            repo.renomear(id, nome(e.nome()));
        }
        if (e.lojas() != null) {
            repo.definirLojas(id, lojasValidas(e.lojas()));
        }
        if (e.ativo() != null) {
            repo.ativar(id, e.ativo());
        }
        return buscar(id);
    }

    /** Gera outra chave; a antiga para de funcionar na hora. */
    public ComChave trocarChave(long id) {
        buscar(id);
        String chave = novaChave();
        repo.trocarChave(id, AuthService.hash(chave), inicio(chave));
        return new ComChave(buscar(id), chave);
    }

    public void excluir(long id) {
        if (!repo.excluir(id)) {
            throw new AcessoException(HttpStatus.NOT_FOUND, "Acesso não encontrado");
        }
    }

    // ------------------------------------------------------------------ uso de fora

    /** Acesso ativo dono da chave; vazio se a chave não existe ou foi desativada. */
    public Optional<Linha> autenticar(String chave) {
        if (chave == null || chave.isBlank()) {
            return Optional.empty();
        }
        Optional<Linha> a = repo.ativoPorHash(AuthService.hash(chave.strip()));
        a.ifPresent(l -> repo.registrarUso(l.id(), OffsetDateTime.now(clock)));
        return a;
    }

    public List<Loja> lojasDo(Linha a) {
        Map<String, String> nomes = repo.lojasIfood();
        return a.lojas().stream().map(m -> new Loja(m, nomes.getOrDefault(m, m))).toList();
    }

    /**
     * Vendas por dia das lojas liberadas. Padrão: do dia 1º do mês até ontem.
     * {@code loja}: filtra uma só (precisa estar liberada).
     */
    public List<Venda> vendas(Linha a, LocalDate de, LocalDate ate, String loja) {
        LocalDate ontem = LocalDate.now(clock.withZone(zona)).minusDays(1);
        LocalDate fim = ate != null ? ate : ontem;
        LocalDate ini = de != null ? de : fim.withDayOfMonth(1);
        if (ini.isAfter(fim)) {
            throw AcessoException.invalido("A data inicial (de) precisa ser antes da final (ate)");
        }
        if (ChronoUnit.DAYS.between(ini, fim) >= MAX_DIAS) {
            throw AcessoException.invalido("Peça no máximo " + MAX_DIAS + " dias por vez");
        }
        List<String> lojas = a.lojas();
        if (loja != null && !loja.isBlank()) {
            if (!lojas.contains(loja.strip())) {
                throw new AcessoException(HttpStatus.FORBIDDEN, "Esta chave não tem acesso à loja " + loja.strip());
            }
            lojas = List.of(loja.strip());
        }
        List<Venda> out = new ArrayList<>();
        List<Dia> dias = repo.dias(lojas, ini, fim);
        Map<String, ItensDoRelatorio.Valores> rel = relatorio.buscar(
                dias.stream().map(Dia::idLojaPortal).filter(java.util.Objects::nonNull).distinct().toList(), ini, fim);
        for (Dia d : dias) {
            ItensDoRelatorio.Valores r = d.idLojaPortal() == null ? null
                    : rel.get(ItensDoRelatorio.chave(d.idLojaPortal(), d.data()));
            BigDecimal ticket = d.pedidos() > 0 && d.faturamento() != null
                    ? d.faturamento().divide(BigDecimal.valueOf(d.pedidos()), 2, RoundingMode.HALF_UP) : null;
            out.add(new Venda(d.data(), d.merchantId(), d.loja(), d.pedidos(), d.cancelados(), d.faturamento(),
                    d.faturamentoCancelado(), ticket, d.novosClientes(), d.atualizadoEm(), d.idLojaPortal(),
                    r != null ? r.itens() : d.pedidos() == 0 ? BigDecimal.ZERO : null,
                    r != null ? r.taxaEntrega() : d.pedidos() == 0 ? BigDecimal.ZERO : null,
                    d.descontoLoja(),
                    r != null ? Integer.valueOf(r.canceladosPagos()) : null,
                    r != null ? r.itensCanceladosPagos() : null));
        }
        return out;
    }

    // ------------------------------------------------------------------ apoio

    private Acesso buscar(long id) {
        return repo.porId(id).map(l -> acesso(l, repo.lojasIfood()))
                .orElseThrow(() -> new AcessoException(HttpStatus.NOT_FOUND, "Acesso não encontrado"));
    }

    private static Acesso acesso(Linha l, Map<String, String> nomes) {
        return new Acesso(l.id(), l.nome(), l.prefixo() + "…", l.ativo(), l.criadoPor(), l.criadoEm(), l.ultimoUso(),
                l.usos(), l.lojas().stream().map(m -> new Loja(m, nomes.getOrDefault(m, m))).toList());
    }

    private String novaChave() {
        return PREFIXO + auth.novoToken();
    }

    private static String inicio(String chave) {
        return chave.substring(0, Math.min(10, chave.length()));
    }

    private static String nome(String nome) {
        if (nome == null || nome.isBlank()) {
            throw AcessoException.invalido("Dê um nome para o acesso (ex.: BI de metas — Fulano)");
        }
        String n = nome.strip();
        return n.length() > 120 ? n.substring(0, 120) : n;
    }

    private List<String> lojasValidas(List<String> lojas) {
        if (lojas == null || lojas.isEmpty()) {
            throw AcessoException.invalido("Escolha pelo menos uma loja");
        }
        Map<String, String> nomes = repo.lojasIfood();
        List<String> out = new ArrayList<>(new LinkedHashSet<>(lojas));
        for (String l : out) {
            if (!nomes.containsKey(l)) {
                throw AcessoException.invalido("Loja não encontrada (só lojas do iFood têm dados diários): " + l);
            }
        }
        return out;
    }
}
