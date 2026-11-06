package com.precificacao.precificacao.cardapio;

import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Linha;
import com.precificacao.precificacao.cardapio.CardapioRepository.Estado;
import com.precificacao.precificacao.cardapio.CardapioRepository.Pausa;
import com.precificacao.precificacao.cardapio.MensagensItens.Mudancas;
import com.precificacao.precificacao.cardapio.MensagensItens.Retorno;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.monitor.EnvioWhatsapp;
import com.precificacao.precificacao.monitor.GrupoWhatsappRepository;
import com.precificacao.precificacao.monitor.MonitorModelos.Grupo;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorProperties;
import com.precificacao.precificacao.monitor.MonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Monitor de itens pausados. O iFood não avisa quando um item é pausado, então de tempos em tempos
 * o cardápio de cada loja é relido e comparado com a leitura anterior.
 *
 * - item UNAVAILABLE → "pausado"; categoria UNAVAILABLE → "categoria" (uma linha só na mensagem);
 *   item disponível mas em /unsellableItems → "não vendável"; complemento UNAVAILABLE → "pausado".
 * - o que já estava pausado na 1ª leitura fica registrado, mas não gera alerta;
 * - com a loja fechada só registra; se ela abrir com a pausa ainda ativa, aí avisa;
 * - mudanças feitas pela própria tela (modo silencioso) ficam no histórico, mas não viram alerta.
 */
@Service
public class MonitorItensService {

    private static final Logger log = LoggerFactory.getLogger(MonitorItensService.class);

    private final ItensProperties cfg;
    private final MonitorProperties monitorCfg;
    private final LeitorCardapio leitor;
    private final CardapioRepository repo;
    private final MonitorRepository monitorRepo;
    private final GrupoWhatsappRepository grupos;
    private final EnvioWhatsapp envio;
    private final Clock clock;
    private final ZoneId zona;

    public MonitorItensService(ItensProperties cfg, MonitorProperties monitorCfg, LeitorCardapio leitor,
                               CardapioRepository repo, MonitorRepository monitorRepo, GrupoWhatsappRepository grupos,
                               EnvioWhatsapp envio, Clock clock, IfoodProperties ifood) {
        this.cfg = cfg;
        this.monitorCfg = monitorCfg;
        this.leitor = leitor;
        this.repo = repo;
        this.monitorRepo = monitorRepo;
        this.grupos = grupos;
        this.envio = envio;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    public boolean ligado() {
        return monitorCfg.ativo() && cfg.ativo();
    }

    /** Relê o cardápio de todas as lojas, registra as mudanças e avisa os grupos. */
    public List<Mudancas> ciclo() {
        List<String> ids = monitorRepo.lojasAtivas().stream().map(LojaMerchant::merchantId).toList();
        Map<String, Object> lidos = new LinkedHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, cfg.workers()));
        try {
            Map<String, Future<Cardapio>> futuros = new LinkedHashMap<>();
            ids.forEach(id -> futuros.put(id, pool.submit(() -> leitor.ler(id))));
            for (var e : futuros.entrySet()) {
                try {
                    lidos.put(e.getKey(), e.getValue().get());
                } catch (Exception ex) {
                    Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
                    lidos.put(e.getKey(), causa);
                }
            }
        } finally {
            pool.shutdown();
        }
        List<Mudancas> mudancas = new ArrayList<>();
        for (var e : lidos.entrySet()) {
            if (e.getValue() instanceof Cardapio c) {
                repo.salvarCardapio(c);
                Mudancas m = registrar(e.getKey(), c, false);
                if (!m.vazia()) {
                    mudancas.add(m);
                }
            } else {
                Throwable t = (Throwable) e.getValue();
                log.warn("Cardápio de {} não lido: {}", e.getKey(), t.getMessage());
                repo.erroLeitura(e.getKey(), t.getMessage(), agora());
            }
        }
        despachar(mudancas);
        return mudancas;
    }

    /** Lê uma loja agora (botão "Atualizar" da tela) e avisa o grupo se achar pausa nova. */
    public Cardapio lerLoja(String merchantId) {
        Cardapio c;
        try {
            c = leitor.ler(merchantId);
        } catch (RuntimeException e) {
            repo.erroLeitura(merchantId, e.getMessage(), agora());
            throw e;
        }
        repo.salvarCardapio(c);
        Mudancas m = registrar(merchantId, c, false);
        if (!m.vazia()) {
            despachar(List.of(m));
        }
        return c;
    }

    /**
     * Compara o cardápio com o estado salvo e registra pausas/retornos.
     * silencioso=true: mudança feita pela própria tela; registra sem avisar ninguém.
     */
    public synchronized Mudancas registrar(String merchantId, Cardapio c, boolean silencioso) {
        OffsetDateTime agora = agora();
        String loja = monitorRepo.nomesDasLojas().getOrDefault(merchantId, merchantId);
        Map<String, Estado> antes = repo.estados(merchantId);
        boolean primeiraLeitura = antes.isEmpty();

        Map<String, Linha> atuais = new LinkedHashMap<>();
        for (Linha l : CardapioModelos.linhas(c, cfg.complementos(), cfg.categoriasIgnoradas())) {
            atuais.putIfAbsent(l.chave(), l);
        }

        List<Linha> salvar = new ArrayList<>();
        Map<String, String> problemas = new HashMap<>();
        Map<String, OffsetDateTime> desde = new HashMap<>();
        List<Retorno> voltaram = new ArrayList<>();

        for (Linha l : atuais.values()) {
            String problema = l.problema(cfg.alertarNaoVendavel());
            String detalhe = "NAO_VENDAVEL".equals(problema) ? l.naoVendavel() : null;
            problemas.put(l.chave(), problema);
            Estado velho = antes.get(l.chave());

            if (velho == null) {
                desde.put(l.chave(), agora);
                salvar.add(l);
                if (problema != null) {
                    repo.abrirPausa(l, problema, detalhe, agora, primeiraLeitura || silencioso, false);
                }
                continue;
            }

            boolean mudou = !Objects.equals(velho.status(), l.status())
                    || !Objects.equals(velho.statusCategoria(), l.statusCategoria())
                    || !Objects.equals(velho.naoVendavel(), l.naoVendavel())
                    || precoDiferente(velho.preco(), l.preco())
                    || !Objects.equals(velho.nome(), l.nome())
                    || !Objects.equals(velho.categoria(), l.categoria())
                    || !Objects.equals(velho.problema(), problema);
            desde.put(l.chave(), Objects.equals(velho.problema(), problema) ? velho.desde() : agora);
            if (mudou) {
                salvar.add(l);
            }
            if (!Objects.equals(velho.problema(), problema)) {
                if (velho.problema() != null && problema != null) {
                    repo.atualizarPausa(merchantId, l.chave(), problema, detalhe);
                } else if (problema != null) {
                    repo.abrirPausa(l, problema, detalhe, agora, silencioso, false);
                } else {
                    repo.fecharPausa(merchantId, l.chave(), agora, "VOLTOU")
                            .filter(p -> p.avisado() && !silencioso)
                            .ifPresent(p -> voltaram.add(new Retorno(p, agora)));
                }
            }
        }

        Set<String> removidos = new LinkedHashSet<>(antes.keySet());
        removidos.removeAll(atuais.keySet());
        removidos.forEach(chave -> repo.fecharPausa(merchantId, chave, agora, "REMOVIDO"));
        repo.salvarEstados(salvar, problemas, desde, agora);
        repo.apagarEstados(merchantId, removidos);

        List<Pausa> avisar = List.of();
        if (!silencioso && (!cfg.somenteComLojaAberta() || lojaAberta(merchantId))) {
            avisar = repo.pausasPendentes(merchantId);
            repo.marcarAvisadas(avisar.stream().map(Pausa::id).toList());
        }
        return new Mudancas(merchantId, loja, avisar, voltaram);
    }

    /** Uma mensagem por grupo, juntando todas as lojas dele. */
    void despachar(List<Mudancas> mudancas) {
        if (mudancas.isEmpty()) {
            return;
        }
        List<Grupo> todos = grupos.listar(true);
        Map<Long, List<Mudancas>> porGrupo = new LinkedHashMap<>();
        Map<Long, Grupo> grupoPorId = new HashMap<>();
        for (Mudancas m : mudancas) {
            for (Grupo g : GrupoWhatsappRepository.gruposDaLoja(todos, m.merchantId())) {
                porGrupo.computeIfAbsent(g.id(), k -> new ArrayList<>()).add(m);
                grupoPorId.put(g.id(), g);
            }
        }
        if (porGrupo.isEmpty()) {
            log.warn("[itens sem grupo] {}", MensagensItens.mensagem(mudancas, true, zona, agora()));
        }
        porGrupo.forEach((id, lista) -> {
            Grupo g = grupoPorId.get(id);
            String texto = MensagensItens.mensagem(lista, g.avisarRetorno(), zona, agora());
            if (!texto.isBlank()) {
                envio.enviar("itens", g, texto);
            }
        });
    }

    /** Loja aberta = alguma operação OK ou com restrição. Sem leitura de status, considera aberta. */
    boolean lojaAberta(String merchantId) {
        var status = monitorRepo.statusAtual().entrySet().stream()
                .filter(e -> e.getKey().merchantId().equals(merchantId))
                .map(e -> e.getValue().estado())
                .toList();
        return status.isEmpty() || status.stream().anyMatch(s -> "OK".equals(s) || "WARNING".equals(s));
    }

    private static boolean precoDiferente(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a != b;
        }
        return a.compareTo(b) != 0;
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock);
    }
}
