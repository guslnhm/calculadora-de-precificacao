package com.precificacao.precificacao.reajuste;

import com.precificacao.precificacao.cardapio.CardapioModelos;
import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Catalogo;
import com.precificacao.precificacao.cardapio.CardapioModelos.Categoria;
import com.precificacao.precificacao.cardapio.CardapioModelos.Grupo;
import com.precificacao.precificacao.cardapio.CardapioModelos.Item;
import com.precificacao.precificacao.cardapio.CardapioModelos.Opcao;
import com.precificacao.precificacao.cardapio.CardapioRepository;
import com.precificacao.precificacao.cardapio.CardapioService;
import com.precificacao.precificacao.cardapio.CardapioService.MudancaPreco;
import com.precificacao.precificacao.cardapio.CardapioService.Recusado;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.enums.Arredondamento;
import com.precificacao.precificacao.enums.ModoCupomIfood;
import com.precificacao.precificacao.reajuste.ReajusteRepository.LojaIfood;
import com.precificacao.precificacao.reajuste.ReajusteRepository.Lote;
import com.precificacao.precificacao.reajuste.ReajusteRepository.Vinculo;
import com.precificacao.precificacao.repository.ItemRepository;
import com.precificacao.precificacao.repository.LojaRepository;
import com.precificacao.precificacao.service.precificacao.CalculadoraPrecoIfood;
import com.precificacao.precificacao.service.precificacao.CalculadoraPrecoIfood.Percentuais;
import com.precificacao.precificacao.service.precificacao.CalculadoraPrecoIfood.Regras;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Reajuste geral: a calculadora (CMV + percentuais da loja + regra de cupom/arredondamento) diz o preço novo,
 * cada item/complemento do iFood é ligado a um item da calculadora pelo nome, e os preços são publicados em lote,
 * com progresso e "desfazer tudo".
 */
@Service
public class ReajusteService {

    private static final Logger log = LoggerFactory.getLogger(ReajusteService.class);
    public static final String CALCULADO = "CALCULADO";
    public static final String SALVO = "SALVO";
    static final double SUGERIR_ITEM = 0.55;
    static final double SUGERIR_COMPLEMENTO = 0.9;

    // ------------------------------------------------------------------ respostas

    public record RegrasLoja(long lojaId, String nome, String modoCupom, String arredondamento, BigDecimal fretePadrao,
                             BigDecimal percentualTotal, BigDecimal percentualCustos) {
    }

    public record ItemCalculadora(long id, String nome, BigDecimal cmv, BigDecimal frete, BigDecimal precoCalculado,
                                  BigDecimal precoSalvo) {
    }

    public record Sugestao(long itemId, String itemNome, double similaridade) {
    }

    public record EntidadeIfood(String tipo, String ifoodId, String nome, String categoria, BigDecimal preco,
                                String status, Vinculo vinculo, Sugestao sugestao) {
    }

    public record VinculosLoja(String merchantId, String loja, OffsetDateTime lidoEm, List<EntidadeIfood> entidades,
                               List<ItemCalculadora> itensCalculadora) {
    }

    public record VinculoRequest(String tipo, String ifoodId, String ifoodNome, Long itemId, boolean ignorado) {
    }

    public record LinhaPrevia(String chave, String merchantId, String loja, String tipo, String ifoodId, String ifoodNome,
                              String categoria, String status, long itemId, String itemNome, BigDecimal cmv,
                              BigDecimal precoAtual, BigDecimal precoNovo, BigDecimal variacao, BigDecimal margemAtual,
                              BigDecimal margemNova, boolean muda, String aviso) {
    }

    public record Previa(RegrasLoja regras, String fonte, List<LinhaPrevia> linhas, List<String> avisos) {
    }

    // ------------------------------------------------------------------

    private final ReajusteRepository repo;
    private final CardapioRepository cardapios;
    private final CardapioService cardapioService;
    private final LojaRepository lojas;
    private final ItemRepository itens;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Executor executor;

    @org.springframework.beans.factory.annotation.Autowired
    public ReajusteService(ReajusteRepository repo, CardapioRepository cardapios, CardapioService cardapioService,
                           LojaRepository lojas, ItemRepository itens, JdbcTemplate jdbc, Clock clock) {
        // um reajuste por vez, para não estourar o limite de chamadas do iFood
        this(repo, cardapios, cardapioService, lojas, itens, jdbc, clock, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "reajuste-ifood");
            t.setDaemon(true);
            return t;
        }));
    }

    public ReajusteService(ReajusteRepository repo, CardapioRepository cardapios, CardapioService cardapioService,
                           LojaRepository lojas, ItemRepository itens, JdbcTemplate jdbc, Clock clock, Executor executor) {
        this.repo = repo;
        this.cardapios = cardapios;
        this.cardapioService = cardapioService;
        this.lojas = lojas;
        this.itens = itens;
        this.jdbc = jdbc;
        this.clock = clock;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        int n = repo.fecharLotesInterrompidos(agora());
        if (n > 0) {
            log.warn("{} reajuste(s) ficaram pela metade quando o servidor reiniciou; marcados como concluídos", n);
        }
    }

    // ------------------------------------------------------------------ regras e lojas do iFood

    public RegrasLoja regras(long lojaId) {
        Loja l = loja(lojaId);
        Percentuais p = Percentuais.daLoja(l);
        return new RegrasLoja(l.getId(), l.getNome(), l.getIfoodModoCupom().name(), l.getIfoodArredondamento().name(),
                l.getIfoodFretePadrao(), p.somaTotal(), p.somaCustos());
    }

    public RegrasLoja salvarRegras(long lojaId, String modoCupom, String arredondamento, BigDecimal fretePadrao) {
        Loja l = loja(lojaId);
        try {
            if (modoCupom != null) {
                l.setIfoodModoCupom(ModoCupomIfood.valueOf(modoCupom));
            }
            if (arredondamento != null) {
                l.setIfoodArredondamento(Arredondamento.valueOf(arredondamento));
            }
        } catch (IllegalArgumentException e) {
            throw new Recusado("Regra inválida: " + e.getMessage());
        }
        if (fretePadrao != null) {
            if (fretePadrao.signum() < 0) {
                throw new Recusado("Frete não pode ser negativo");
            }
            l.setIfoodFretePadrao(fretePadrao.setScale(2, RoundingMode.HALF_UP));
        }
        lojas.save(l);
        return regras(lojaId);
    }

    public List<LojaIfood> todasLojasIfood() {
        return repo.lojasIfood();
    }

    public List<LojaIfood> lojasIfood(long lojaId) {
        return repo.lojasIfoodDaLoja(lojaId);
    }

    public List<LojaIfood> vincularLojas(long lojaId, Collection<String> merchantIds) {
        loja(lojaId);
        repo.vincularLojas(lojaId, new LinkedHashSet<>(merchantIds));
        return repo.lojasIfoodDaLoja(lojaId);
    }

    // ------------------------------------------------------------------ vínculos de itens

    public VinculosLoja vinculos(long lojaId, String merchantId) {
        Loja loja = loja(lojaId);
        exigirLojaDaCalculadora(lojaId, merchantId);
        Cardapio c = cardapioService.cardapio(merchantId, false);
        List<ItemCalculadora> calc = itensCalculadora(loja);
        Map<String, Vinculo> salvos = repo.vinculos(merchantId);
        List<EntidadeIfood> out = new ArrayList<>();
        for (EntidadeIfood e : entidades(c)) {
            Vinculo v = salvos.get(merchantId + "|" + e.tipo() + "|" + e.ifoodId());
            Sugestao s = v == null ? sugerir(e, calc) : null;
            out.add(new EntidadeIfood(e.tipo(), e.ifoodId(), e.nome(), e.categoria(), e.preco(), e.status(), v, s));
        }
        String nome = repo.lojasIfoodDaLoja(lojaId).stream().filter(x -> x.merchantId().equals(merchantId))
                .map(LojaIfood::nome).findFirst().orElse(merchantId);
        return new VinculosLoja(merchantId, nome, c.lidoEm(), out, calc);
    }

    public void salvarVinculos(long lojaId, String merchantId, List<VinculoRequest> pedidos, String usuario) {
        exigirLojaDaCalculadora(lojaId, merchantId);
        Set<Long> idsDaLoja = new LinkedHashSet<>();
        itens.findByLojaIdAndAtivoTrue(lojaId).forEach(i -> idsDaLoja.add(i.getId()));
        OffsetDateTime agora = agora();
        for (VinculoRequest p : pedidos) {
            String tipo = tipo(p.tipo());
            if (p.itemId() == null && !p.ignorado()) {
                repo.apagarVinculo(merchantId, tipo, p.ifoodId()); // volta a ficar "sem vínculo"
                continue;
            }
            if (p.itemId() != null && !idsDaLoja.contains(p.itemId())) {
                throw new Recusado("O item " + p.itemId() + " não é desta loja da calculadora");
            }
            repo.salvarVinculo(merchantId, tipo, p.ifoodId(), p.ifoodNome(), p.ignorado() ? null : p.itemId(),
                    p.ignorado(), "MANUAL", null, usuario, agora);
        }
    }

    /** Aceita de uma vez as sugestões com semelhança ≥ mínimo. Devolve quantas foram salvas. */
    public int aceitarSugestoes(long lojaId, String merchantId, double minimo, String usuario) {
        VinculosLoja v = vinculos(lojaId, merchantId);
        int n = 0;
        OffsetDateTime agora = agora();
        for (EntidadeIfood e : v.entidades()) {
            if (e.vinculo() == null && e.sugestao() != null && e.sugestao().similaridade() >= minimo) {
                repo.salvarVinculo(merchantId, e.tipo(), e.ifoodId(), e.nome(), e.sugestao().itemId(), false, "AUTO",
                        BigDecimal.valueOf(e.sugestao().similaridade()).setScale(3, RoundingMode.HALF_UP), usuario, agora);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ importar itens do iFood

    public record Importacao(int criados, int ligadosAExistentes, int jaLigados, List<String> lojas) {
    }

    /**
     * Traz para a calculadora os itens (não complementos) do cardápio das lojas do iFood ligadas a esta loja.
     * Cada item já fica ligado ao item do iFood; item com o mesmo nome (rede, ou o mesmo produto em duas categorias)
     * usa o mesmo item da calculadora. O preço atual do iFood vira o ponto de partida; o custo fica para a planilha.
     */
    public Importacao importarDoIfood(long lojaId, String merchantId, String usuario) {
        Loja loja = loja(lojaId);
        List<LojaIfood> ligadas = repo.lojasIfoodDaLoja(lojaId);
        if (merchantId != null && !merchantId.isBlank()
                && ligadas.stream().noneMatch(l -> l.merchantId().equals(merchantId))) {
            List<String> ids = new ArrayList<>(ligadas.stream().map(LojaIfood::merchantId).toList());
            ids.add(merchantId);
            repo.vincularLojas(lojaId, ids);
            ligadas = repo.lojasIfoodDaLoja(lojaId);
        }
        if (ligadas.isEmpty()) {
            throw new Recusado("Ligue pelo menos uma loja do iFood a esta loja antes de importar");
        }
        Map<String, Long> porNome = new LinkedHashMap<>();
        itens.findByLojaIdAndAtivoTrue(lojaId)
                .forEach(i -> porNome.putIfAbsent(NomesParecidos.normalizar(i.getNomeItem()), i.getId()));
        int criados = 0;
        int ligados = 0;
        int jaLigados = 0;
        OffsetDateTime agora = agora();
        for (LojaIfood li : ligadas) {
            Cardapio c = cardapioService.cardapio(li.merchantId(), false);
            Map<String, Vinculo> salvos = repo.vinculos(li.merchantId());
            for (EntidadeIfood e : entidades(c)) {
                if (!CardapioModelos.ITEM.equals(e.tipo())) {
                    continue;
                }
                if (salvos.containsKey(li.merchantId() + "|" + e.tipo() + "|" + e.ifoodId())) {
                    jaLigados++;
                    continue;
                }
                String chaveNome = NomesParecidos.normalizar(e.nome());
                Long itemId = porNome.get(chaveNome);
                if (itemId == null) {
                    var item = new com.precificacao.precificacao.entity.Item();
                    item.setLoja(loja);
                    item.setNomeItem(e.nome().length() > 150 ? e.nome().substring(0, 150) : e.nome());
                    item.setCmv(BigDecimal.ZERO); // sem custo ainda: vem da planilha
                    item.setPrecoVendaInicial(e.preco());
                    item.setPrecoVendaAtualIfood(e.preco());
                    item.setObservacao("Importado do iFood");
                    item.setAtivo(true);
                    item.setCriadoEm(java.time.LocalDateTime.now());
                    item.setAtualizadoEm(java.time.LocalDateTime.now());
                    itemId = itens.save(item).getId();
                    porNome.put(chaveNome, itemId);
                    criados++;
                } else {
                    ligados++;
                }
                repo.salvarVinculo(li.merchantId(), e.tipo(), e.ifoodId(), e.nome(), itemId, false, "IMPORTADO",
                        BigDecimal.ONE, usuario, agora);
            }
        }
        return new Importacao(criados, ligados, jaLigados, ligadas.stream().map(LojaIfood::nome).toList());
    }

    // ------------------------------------------------------------------ calcular e salvar preços em massa

    public record PrecoItem(long itemId, String nome, BigDecimal cmv, BigDecimal frete, BigDecimal precoSalvo,
                            BigDecimal precoIfood, BigDecimal valorBase, Map<String, BigDecimal> opcoes,
                            Map<String, BigDecimal> margens, String sugerido, boolean semCusto) {
    }

    public record Precos(RegrasLoja regras, List<PrecoItem> itens) {
    }

    public record SalvarPreco(Long itemId, BigDecimal preco) {
    }

    /** Para cada item da loja: o preço da calculadora com cada cupom (base, +5, +8, +10), já com o arredondamento. */
    public Precos precos(long lojaId) {
        Loja loja = loja(lojaId);
        Percentuais p = Percentuais.daLoja(loja);
        if (p.somaTotal().compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new Recusado("A soma dos percentuais da loja passa de 100%: confira os percentuais na calculadora");
        }
        Map<Long, BigDecimal> noIfood = precoAtualNoIfood(lojaId);
        List<PrecoItem> out = new ArrayList<>();
        for (var i : itens.findByLojaIdAndAtivoTrue(lojaId)) {
            boolean semCusto = i.getCmv() == null || i.getCmv().signum() <= 0;
            BigDecimal frete = i.getFrete() != null ? i.getFrete() : loja.getIfoodFretePadrao();
            Map<String, BigDecimal> opcoes = new LinkedHashMap<>();
            Map<String, BigDecimal> margens = new LinkedHashMap<>();
            BigDecimal base = null;
            if (!semCusto) {
                for (ModoCupomIfood modo : ModoCupomIfood.values()) {
                    var r = CalculadoraPrecoIfood.calcular(i.getCmv(), i.getFrete(), p,
                            new Regras(loja.getIfoodFretePadrao(), modo, loja.getIfoodArredondamento()));
                    base = r.valorBase();
                    opcoes.put(modo.name(), r.precoFinal());
                    margens.put(modo.name(), CalculadoraPrecoIfood.margemPercentual(r.precoFinal(), i.getCmv(), frete, p));
                }
            }
            out.add(new PrecoItem(i.getId(), i.getNomeItem(), i.getCmv(), i.getFrete(), i.getPrecoVendaAtualIfood(),
                    noIfood.get(i.getId()), base, opcoes, margens, loja.getIfoodModoCupom().name(), semCusto));
        }
        out.sort((a, b) -> a.nome().compareToIgnoreCase(b.nome()));
        return new Precos(regras(lojaId), out);
    }

    /** Salva o "preço de venda iFood" dos itens escolhidos (é ele que a revisão compara com o iFood). */
    public int salvarPrecos(long lojaId, List<SalvarPreco> lista) {
        loja(lojaId);
        Set<Long> daLoja = new LinkedHashSet<>();
        itens.findByLojaIdAndAtivoTrue(lojaId).forEach(i -> daLoja.add(i.getId()));
        int n = 0;
        for (SalvarPreco s : lista == null ? List.<SalvarPreco>of() : lista) {
            if (s.itemId() == null || !daLoja.contains(s.itemId())) {
                throw new Recusado("Item " + s.itemId() + " não é desta loja");
            }
            if (s.preco() == null || s.preco().signum() <= 0) {
                throw new Recusado("Preço inválido para o item " + s.itemId());
            }
            n += jdbc.update("UPDATE itens SET preco_venda_atual_ifood = ?, data_precificacao = now(), atualizado_em = now() "
                    + "WHERE id = ?", s.preco().setScale(2, RoundingMode.HALF_UP), s.itemId());
        }
        return n;
    }

    /** Preço atual no iFood de cada item da calculadora (pela 1ª loja do iFood ligada que tiver o item). */
    private Map<Long, BigDecimal> precoAtualNoIfood(long lojaId) {
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        for (LojaIfood li : repo.lojasIfoodDaLoja(lojaId)) {
            Optional<Cardapio> c = cardapios.cardapio(li.merchantId());
            if (c.isEmpty()) {
                continue;
            }
            Map<String, BigDecimal> precos = new LinkedHashMap<>();
            entidades(c.get()).forEach(e -> precos.put(e.tipo() + "|" + e.ifoodId(), e.preco()));
            for (Vinculo v : repo.vinculos(li.merchantId()).values()) {
                if (v.itemId() != null && !v.ignorado() && CardapioModelos.ITEM.equals(v.tipo())) {
                    BigDecimal preco = precos.get(v.tipo() + "|" + v.ifoodId());
                    if (preco != null) {
                        out.putIfAbsent(v.itemId(), preco);
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ prévia e publicação

    public Previa previa(long lojaId, String fonte) {
        String f = SALVO.equalsIgnoreCase(fonte) ? SALVO : CALCULADO;
        Loja loja = loja(lojaId);
        Percentuais p = Percentuais.daLoja(loja);
        List<String> avisos = new ArrayList<>();
        if (p.somaTotal().compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new Recusado("A soma dos percentuais da loja passa de 100%: confira os percentuais na calculadora");
        }
        Map<Long, ItemCalculadora> calc = new LinkedHashMap<>();
        itensCalculadora(loja).forEach(i -> calc.put(i.id(), i));

        List<LinhaPrevia> linhas = new ArrayList<>();
        List<LojaIfood> lojasIfood = repo.lojasIfoodDaLoja(lojaId);
        if (lojasIfood.isEmpty()) {
            avisos.add("Esta loja ainda não tem lojas do iFood ligadas.");
        }
        for (LojaIfood li : lojasIfood) {
            Optional<Cardapio> c = cardapios.cardapio(li.merchantId());
            if (c.isEmpty()) {
                avisos.add("O cardápio de " + li.nome() + " ainda não foi lido: abra a loja em Cardápio iFood.");
                continue;
            }
            Map<String, EntidadeIfood> ents = new LinkedHashMap<>();
            entidades(c.get()).forEach(e -> ents.put(e.tipo() + "|" + e.ifoodId(), e));
            int semVinculo = 0;
            Map<String, Vinculo> vinculosDaLoja = repo.vinculos(li.merchantId());
            for (Vinculo v : vinculosDaLoja.values()) {
                EntidadeIfood e = ents.get(v.tipo() + "|" + v.ifoodId());
                if (e == null || v.ignorado() || v.itemId() == null) {
                    continue;
                }
                ItemCalculadora item = calc.get(v.itemId());
                if (item == null) {
                    continue; // item desativado na calculadora
                }
                BigDecimal novo = SALVO.equals(f) ? item.precoSalvo() : item.precoCalculado();
                String aviso = novo == null ? "Sem preço de venda iFood salvo na calculadora" : null;
                if (CALCULADO.equals(f) && (item.cmv() == null || item.cmv().signum() <= 0)) {
                    aviso = "Sem custo na calculadora (preencha pela planilha)";
                    novo = null;
                }
                if (novo != null && novo.signum() == 0 && CardapioModelos.ITEM.equals(e.tipo())) {
                    aviso = "Preço calculado R$ 0,00 (CMV zerado?)";
                    novo = null;
                }
                BigDecimal frete = item.frete() != null ? item.frete() : loja.getIfoodFretePadrao();
                boolean muda = novo != null && (e.preco() == null || e.preco().compareTo(novo) != 0);
                BigDecimal variacao = novo != null && e.preco() != null && e.preco().signum() > 0
                        ? novo.subtract(e.preco()).multiply(BigDecimal.valueOf(100)).divide(e.preco(), 1, RoundingMode.HALF_UP)
                        : null;
                linhas.add(new LinhaPrevia(v.chave(), li.merchantId(), li.nome(), e.tipo(), e.ifoodId(), e.nome(),
                        e.categoria(), e.status(), item.id(), item.nome(), item.cmv(), e.preco(), novo, variacao,
                        CalculadoraPrecoIfood.margemPercentual(e.preco(), item.cmv(), frete, p),
                        CalculadoraPrecoIfood.margemPercentual(novo, item.cmv(), frete, p), muda, aviso));
            }
            for (EntidadeIfood e : ents.values()) {
                if (CardapioModelos.ITEM.equals(e.tipo())
                        && !vinculosDaLoja.containsKey(li.merchantId() + "|" + e.tipo() + "|" + e.ifoodId())) {
                    semVinculo++;
                }
            }
            if (semVinculo > 0) {
                avisos.add(li.nome() + ": " + semVinculo + " item(ns) do iFood ainda fora da calculadora (não entram no reajuste): use “Importar itens do iFood” na aba 1.");
            }
        }
        linhas.sort((a, b) -> {
            int x = a.loja().compareToIgnoreCase(b.loja());
            if (x != 0) {
                return x;
            }
            x = String.valueOf(a.categoria()).compareToIgnoreCase(String.valueOf(b.categoria()));
            return x != 0 ? x : a.ifoodNome().compareToIgnoreCase(b.ifoodNome());
        });
        return new Previa(regras(lojaId), f, linhas, avisos);
    }

    /** Publica os preços das linhas escolhidas da prévia (em segundo plano). Devolve o lote para acompanhar. */
    public Lote publicar(long lojaId, String fonte, Collection<String> chaves, String usuario) {
        Previa previa = previa(lojaId, fonte);
        Set<String> escolhidas = new LinkedHashSet<>(chaves == null ? List.of() : chaves);
        Map<String, List<MudancaPreco>> porLoja = new LinkedHashMap<>();
        Map<String, Long> itemDaChave = new LinkedHashMap<>();
        int total = 0;
        for (LinhaPrevia l : previa.linhas()) {
            if (escolhidas.contains(l.chave()) && l.muda() && l.precoNovo() != null) {
                porLoja.computeIfAbsent(l.merchantId(), k -> new ArrayList<>())
                        .add(new MudancaPreco(l.tipo(), l.ifoodId(), l.precoNovo(), null));
                itemDaChave.put(l.merchantId() + "|" + l.tipo() + "|" + l.ifoodId(), l.itemId());
                total++;
            }
        }
        if (total == 0) {
            throw new Recusado("Nenhum preço para mudar nas linhas escolhidas");
        }
        long loteId = repo.criarLote(lojaId, usuario, previa.fonte(), total, agora());
        executor.execute(() -> rodar(loteId, porLoja, usuario, itemDaChave, false));
        return repo.lote(loteId).orElseThrow();
    }

    /** Volta todos os preços de um lote para o valor de antes (em segundo plano). */
    public Lote desfazer(long loteId, String usuario) {
        Lote lote = repo.lote(loteId).orElseThrow(() -> new Recusado("Reajuste não encontrado"));
        if (!"CONCLUIDO".equals(lote.situacao())) {
            throw new Recusado("Só dá para desfazer um reajuste concluído (situação: " + lote.situacao() + ")");
        }
        Map<String, List<MudancaPreco>> porLoja = new LinkedHashMap<>();
        Map<String, Long> itemDaChave = new LinkedHashMap<>();
        Map<String, Map<String, Vinculo>> vinculosPorLoja = new LinkedHashMap<>();
        for (Map<String, Object> a : repo.alteracoesDoLote(loteId)) {
            if ("FEITA".equals(a.get("situacao")) && a.get("antes") != null && a.get("desfaz_id") == null) {
                String merchant = (String) a.get("merchant_id");
                String chave = merchant + "|" + a.get("tipo") + "|" + a.get("entidade_id");
                Vinculo v = vinculosPorLoja.computeIfAbsent(merchant, repo::vinculos).get(chave);
                if (v != null && v.itemId() != null) {
                    itemDaChave.put(chave, v.itemId());
                }
                porLoja.computeIfAbsent(merchant, k -> new ArrayList<>()).add(new MudancaPreco((String) a.get("tipo"),
                        (String) a.get("entidade_id"), new BigDecimal((String) a.get("antes")), ((Number) a.get("id")).longValue()));
            }
        }
        if (porLoja.isEmpty()) {
            throw new Recusado("Nada para desfazer neste reajuste");
        }
        repo.situacao(loteId, "DESFAZENDO", agora());
        repo.desfeitoPor(loteId, usuario);
        executor.execute(() -> rodar(loteId, porLoja, usuario, itemDaChave, true));
        return repo.lote(loteId).orElseThrow();
    }

    public Optional<Lote> lote(long id) {
        return repo.lote(id);
    }

    public List<Lote> lotes(long lojaId) {
        return repo.lotes(lojaId);
    }

    public List<Map<String, Object>> alteracoesDoLote(long id) {
        return repo.alteracoesDoLote(id);
    }

    private void rodar(long loteId, Map<String, List<MudancaPreco>> porLoja, String usuario, Map<String, Long> itemDaChave,
                       boolean desfazendo) {
        try {
            for (var e : porLoja.entrySet()) {
                String merchant = e.getKey();
                java.util.concurrent.atomic.AtomicInteger processadas = new java.util.concurrent.atomic.AtomicInteger();
                try {
                    cardapioService.alterarPrecosEmLote(merchant, e.getValue(), usuario, loteId, a -> {
                        processadas.incrementAndGet();
                        boolean ok = "FEITA".equals(a.situacao());
                        if (desfazendo) {
                            if (ok) {
                                repo.contarDesfeita(loteId);
                            }
                        } else {
                            repo.contar(loteId, ok);
                        }
                        Long itemId = itemDaChave.get(merchant + "|" + a.tipo() + "|" + a.entidadeId());
                        if (ok && itemId != null) {
                            guardarPrecoNaCalculadora(itemId, new BigDecimal(a.depois()));
                        }
                    });
                } catch (RuntimeException ex) {
                    log.error("Reajuste {}: falha na loja {}", loteId, merchant, ex);
                    if (!desfazendo) {
                        for (int i = processadas.get(); i < e.getValue().size(); i++) {
                            repo.contar(loteId, false);
                        }
                    }
                }
            }
        } finally {
            repo.situacao(loteId, desfazendo ? "DESFEITO" : "CONCLUIDO", agora());
        }
    }

    /** O preço publicado vira o "preço de venda atual iFood" do item na calculadora. */
    private void guardarPrecoNaCalculadora(long itemId, BigDecimal preco) {
        jdbc.update("UPDATE itens SET preco_venda_atual_ifood = ?, atualizado_em = now() WHERE id = ?", preco, itemId);
    }

    // ------------------------------------------------------------------ apoio

    private Loja loja(long lojaId) {
        return lojas.findById(lojaId).orElseThrow(() -> new Recusado("Loja da calculadora não encontrada"));
    }

    private void exigirLojaDaCalculadora(long lojaId, String merchantId) {
        boolean ligada = repo.lojasIfoodDaLoja(lojaId).stream().anyMatch(l -> l.merchantId().equals(merchantId));
        if (!ligada) {
            throw new Recusado("Esta loja do iFood não está ligada a esta loja da calculadora");
        }
    }

    List<ItemCalculadora> itensCalculadora(Loja loja) {
        Percentuais p = Percentuais.daLoja(loja);
        Regras r = Regras.daLoja(loja);
        boolean percentuaisOk = p.somaTotal().compareTo(BigDecimal.valueOf(100)) < 0;
        List<ItemCalculadora> out = new ArrayList<>();
        for (var i : itens.findByLojaIdAndAtivoTrue(loja.getId())) {
            BigDecimal calculado = percentuaisOk && i.getCmv() != null
                    ? CalculadoraPrecoIfood.calcular(i.getCmv(), i.getFrete(), p, r).precoFinal() : null;
            out.add(new ItemCalculadora(i.getId(), i.getNomeItem(), i.getCmv(), i.getFrete(), calculado,
                    i.getPrecoVendaAtualIfood()));
        }
        out.sort((a, b) -> a.nome().compareToIgnoreCase(b.nome()));
        return out;
    }

    /** Itens e complementos do cardápio (cada um uma vez; categoria = categoria do item ou grupo do complemento). */
    static List<EntidadeIfood> entidades(Cardapio c) {
        Map<String, EntidadeIfood> out = new LinkedHashMap<>();
        for (Catalogo cat : c.catalogos()) {
            for (Categoria k : cat.categorias()) {
                for (Item i : k.itens()) {
                    out.putIfAbsent(CardapioModelos.ITEM + "|" + i.id(), new EntidadeIfood(CardapioModelos.ITEM, i.id(),
                            i.nome(), k.nome(), i.preco(), i.status(), null, null));
                }
            }
        }
        for (Catalogo cat : c.catalogos()) {
            for (Categoria k : cat.categorias()) {
                for (Item i : k.itens()) {
                    for (Grupo g : i.grupos()) {
                        for (Opcao o : g.opcoes()) {
                            out.putIfAbsent(CardapioModelos.COMPLEMENTO + "|" + o.id(), new EntidadeIfood(
                                    CardapioModelos.COMPLEMENTO, o.id(), o.nome(), g.nome(), o.preco(), o.status(), null, null));
                        }
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    static Sugestao sugerir(EntidadeIfood e, List<ItemCalculadora> calc) {
        double minimo = CardapioModelos.ITEM.equals(e.tipo()) ? SUGERIR_ITEM : SUGERIR_COMPLEMENTO;
        ItemCalculadora melhor = null;
        double melhorNota = 0;
        for (ItemCalculadora i : calc) {
            double s = NomesParecidos.semelhanca(e.nome(), i.nome());
            if (s > melhorNota) {
                melhorNota = s;
                melhor = i;
            }
        }
        if (melhor == null || melhorNota < minimo) {
            return null;
        }
        return new Sugestao(melhor.id(), melhor.nome(), Math.round(melhorNota * 1000) / 1000.0);
    }

    static String tipo(String tipo) {
        String t = tipo == null ? "" : tipo.strip().toUpperCase();
        if (t.equals(CardapioModelos.ITEM) || t.equals(CardapioModelos.COMPLEMENTO)) {
            return t;
        }
        throw new Recusado("Tipo inválido: use ITEM ou COMPLEMENTO");
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock);
    }
}
