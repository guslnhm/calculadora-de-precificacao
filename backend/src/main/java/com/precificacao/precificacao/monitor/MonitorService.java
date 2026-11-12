package com.precificacao.precificacao.monitor;

import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.ifood.merchant.TipoMotivo;
import com.precificacao.precificacao.monitor.MonitorModelos.Chave;
import com.precificacao.precificacao.monitor.MonitorModelos.Evento;
import com.precificacao.precificacao.monitor.MonitorModelos.Grupo;
import com.precificacao.precificacao.monitor.MonitorModelos.Incidente;
import com.precificacao.precificacao.monitor.MonitorModelos.IncidenteRelatorio;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorModelos.Pausa;
import com.precificacao.precificacao.monitor.MonitorModelos.StatusSalvo;
import com.precificacao.precificacao.monitor.MonitorModelos.TipoEvento;
import com.precificacao.precificacao.whatsapp.WhatsappSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Monitor de lojas: lê o status de todas as lojas, abre/fecha incidentes e manda
 * os alertas consolidados por grupo de WhatsApp. Mesma lógica do monitor em Python.
 *
 * Só LÊ o status: não faz polling de pedidos, então não interfere na presença da loja no iFood.
 */
@Service
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final MonitorProperties cfg;
    private final FonteIfood ifood;
    private final MonitorRepository repo;
    private final GrupoWhatsappRepository grupos;
    private final WhatsappSender whatsapp;
    private final Clock clock;
    private final ZoneId zona;

    // Estado em memória (reconstruído do banco ao iniciar)
    private boolean carregado = false;
    private Map<String, String> nomes = new HashMap<>();
    private List<String> merchantIds = new ArrayList<>();
    private final Map<Chave, StatusSalvo> atual = new HashMap<>();
    private final Map<Chave, Incidente> abertos = new HashMap<>();
    private final Map<Chave, Integer> ciclosRuins = new HashMap<>();
    private final Map<Chave, OffsetDateTime> ruimDesde = new HashMap<>();
    private final Map<String, Integer> falhas = new HashMap<>();
    private final Set<String> falhaAvisada = new HashSet<>();
    private final Set<String> semGrupoAvisadas = new HashSet<>();
    private boolean falhaGeralAvisada = false;
    private OffsetDateTime lojasAtualizadasEm = null;
    private OffsetDateTime conferidoPor403Em = null;

    public MonitorService(MonitorProperties cfg, FonteIfood ifood, MonitorRepository repo,
                          GrupoWhatsappRepository grupos, WhatsappSender whatsapp, Clock clock,
                          IfoodProperties ifoodProps) {
        this.cfg = cfg;
        this.ifood = ifood;
        this.repo = repo;
        this.grupos = grupos;
        this.whatsapp = whatsapp;
        this.clock = clock;
        this.zona = ifoodProps.zona();
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private synchronized void carregar() {
        if (carregado) {
            return;
        }
        nomes = repo.nomesDasLojas();
        atual.putAll(repo.statusAtual());
        for (Incidente i : repo.incidentesAbertos()) {
            abertos.put(i.chave(), i);
        }
        carregado = true;
    }

    // ------------------------------------------------------------- lojas

    public synchronized void atualizarLojas(boolean forcar) {
        carregar();
        OffsetDateTime agora = agora();
        if (!forcar && lojasAtualizadasEm != null
                && Duration.between(lojasAtualizadasEm, agora).toMinutes() < cfg.atualizarLojasMinutos()) {
            return;
        }
        ifood.renovarAcesso(); // pega permissões de lojas recém-autorizadas
        List<LojaMerchant> lojas = ifood.listarLojas();
        repo.salvarLojas(lojas);
        nomes = new HashMap<>();
        for (LojaMerchant l : lojas) {
            nomes.put(l.merchantId(), l.nome() == null || l.nome().isBlank() ? l.merchantId() : l.nome());
        }
        merchantIds = lojas.stream().map(LojaMerchant::merchantId).toList();
        lojasAtualizadasEm = agora;
        esquecerRemovidas(new HashSet<>(merchantIds), agora);
        log.info("{} lojas vinculadas ao aplicativo", merchantIds.size());

        List<Grupo> todos = grupos.listar(true);
        boolean algumCobreTodas = todos.stream().anyMatch(Grupo::todasLojas);
        Set<String> mapeadas = todos.stream().flatMap(g -> g.lojas().stream()).collect(Collectors.toSet());
        List<String> semGrupo = algumCobreTodas ? List.of() : merchantIds.stream()
                .filter(m -> !mapeadas.contains(m) && !semGrupoAvisadas.contains(m)).toList();
        if (!semGrupo.isEmpty()) {
            semGrupoAvisadas.addAll(semGrupo);
            String lista = semGrupo.stream().map(m -> "• " + nomes.getOrDefault(m, m) + " (" + m + ")")
                    .collect(Collectors.joining("\n"));
            enviarAdmin("🗺️ *Lojas sem grupo no mapa*\nOs alertas destas lojas vão para este grupo até serem mapeadas:\n\n" + lista);
        }
    }

    // ------------------------------------------------------------- ciclo

    /** Um ciclo completo: lê todas as lojas, atualiza incidentes e envia os alertas. */
    public synchronized List<Evento> ciclo() {
        carregar();
        try {
            atualizarLojas(false);
        } catch (RuntimeException e) {
            log.error("Falha ao atualizar a lista de lojas: {}", e.getMessage());
            if (merchantIds.isEmpty()) {
                falhaGeral(e);
                return List.of();
            }
        }

        Map<String, Object> resultados = lerTodas(merchantIds);
        OffsetDateTime agora = agora();
        resultados = semLojasRemovidas(resultados, agora);
        List<Evento> eventos = new ArrayList<>();

        long qtdFalhas = resultados.values().stream().filter(r -> r instanceof Exception).count();
        if (!resultados.isEmpty() && qtdFalhas == resultados.size()) {
            Exception primeira = (Exception) resultados.values().iterator().next();
            falhaGeral(primeira);
        } else if (falhaGeralAvisada && qtdFalhas < resultados.size()) {
            falhaGeralAvisada = false;
            enviarAdmin("✅ *Monitor iFood*: voltei a conseguir ler o status das lojas.");
        }

        Set<String> ignoradas = cfg.operacoesIgnoradas();
        for (Map.Entry<String, Object> r : resultados.entrySet()) {
            String merchantId = r.getKey();
            if (r.getValue() instanceof Exception e) {
                falhaDeLoja(merchantId, e);
                continue;
            }
            if (falhaAvisada.remove(merchantId)) {
                enviarAdmin("✅ Voltei a ler o status de *" + nomes.getOrDefault(merchantId, merchantId) + "*.");
            }
            falhas.put(merchantId, 0);
            @SuppressWarnings("unchecked")
            List<StatusOperacao> lista = (List<StatusOperacao>) r.getValue();
            Set<Chave> vistas = new HashSet<>();
            for (StatusOperacao st : lista) {
                if (ignoradas.contains(st.operacao().toUpperCase())) {
                    continue;
                }
                vistas.add(new Chave(merchantId, st.operacao(), st.canal()));
                Evento ev = processar(merchantId, st, agora);
                if (ev != null) {
                    eventos.add(ev);
                }
            }
            // operação/canal que deixou de vir na resposta: encerra o incidente sem alarde
            for (Chave k : new ArrayList<>(abertos.keySet())) {
                if (k.merchantId().equals(merchantId) && !vistas.contains(k)) {
                    Incidente inc = abertos.remove(k);
                    repo.fecharIncidente(inc.id, agora, "AUSENTE");
                }
            }
        }

        despachar(eventos);
        return eventos;
    }

    private Map<String, Object> lerTodas(List<String> ids) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(cfg.workers(), ids.size())));
        try {
            Map<String, Future<List<StatusOperacao>>> futuros = new LinkedHashMap<>();
            for (String id : ids) {
                futuros.put(id, pool.submit(() -> ifood.status(id)));
            }
            for (Map.Entry<String, Future<List<StatusOperacao>>> f : futuros.entrySet()) {
                try {
                    out.put(f.getKey(), f.getValue().get());
                } catch (java.util.concurrent.ExecutionException e) {
                    out.put(f.getKey(), e.getCause() instanceof Exception c ? c : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    out.put(f.getKey(), e);
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    private Evento processar(String merchantId, StatusOperacao st, OffsetDateTime agora) {
        Chave k = new Chave(merchantId, st.operacao(), st.canal());
        StatusSalvo anterior = atual.get(k);
        String assinaturaAnterior = anterior == null ? null : anterior.assinatura();
        boolean assinaturaMudou = !Objects.equals(assinaturaAnterior, st.assinatura());
        boolean estadoMudou = anterior == null || !anterior.estado().equals(st.estado());
        OffsetDateTime desde = estadoMudou ? agora : anterior.desde();
        repo.salvarVerificacao(merchantId, st, agora, assinaturaMudou, desde);
        atual.put(k, new StatusSalvo(st.estado(), desde, st.assinatura()));
        String nome = nomes.getOrDefault(merchantId, merchantId);

        if (cfg.estadosQueAlertam().contains(st.estado())) {
            Incidente inc = abertos.get(k);
            if (inc != null) {
                if (assinaturaMudou) {
                    repo.atualizarIncidente(inc.id, st);
                    inc.estado = st.estado();
                    inc.motivos = MonitorModelos.motivosDe(st);
                    inc.mensagem = st.mensagem();
                    inc.reabrivel = st.reabrivel();
                    // avisa se mudou o estado OU apareceu/sumiu um motivo (ex.: já desconectada e alguém pausou)
                    if (assinaturaAnterior != null && (!estadoDe(assinaturaAnterior).equals(st.estado())
                            || !codigos(assinaturaAnterior).equals(codigos(st.assinatura())))) {
                        return new Evento(TipoEvento.MUDOU, merchantId, nome, st, inc, agora, pausasSe(merchantId, st), null);
                    }
                }
                int lembrete = cfg.lembreteMinutos();
                OffsetDateTime ultimo = inc.ultimoLembreteEm != null ? inc.ultimoLembreteEm : inc.confirmadoEm;
                if (lembrete > 0 && ultimo != null && Duration.between(ultimo, agora).toMinutes() >= lembrete) {
                    repo.marcarLembrete(inc.id, agora);
                    inc.ultimoLembreteEm = agora;
                    return new Evento(TipoEvento.LEMBRETE, merchantId, nome, st, inc, agora, pausasSe(merchantId, st), null);
                }
                return null;
            }

            int n = ciclosRuins.merge(k, 1, Integer::sum);
            ruimDesde.putIfAbsent(k, agora);
            if (n < cfg.ciclosConfirmacao()) {
                return null;
            }
            List<Pausa> pausas = pausasSe(merchantId, st);
            Incidente novo = repo.abrirIncidente(merchantId, st, ruimDesde.get(k), agora, pausas);
            abertos.put(k, novo);
            ciclosRuins.remove(k);
            ruimDesde.remove(k);
            return new Evento(TipoEvento.ABERTO, merchantId, nome, st, novo, agora, pausas, null);
        }

        // fora do alerta
        ciclosRuins.remove(k);
        ruimDesde.remove(k);
        Incidente inc = abertos.remove(k);
        if (inc != null) {
            repo.fecharIncidente(inc.id, agora, st.estado());
            return new Evento(TipoEvento.NORMALIZADO, merchantId, nome, st, inc, agora, List.of(), st.estado());
        }
        return null;
    }

    private static String estadoDe(String assinatura) {
        int i = assinatura.indexOf('|');
        return i < 0 ? assinatura : assinatura.substring(0, i);
    }

    /** Códigos de motivo de uma assinatura "ESTADO|code:STATE,…", sem os de horário de funcionamento. */
    static Set<String> codigos(String assinatura) {
        int i = assinatura.indexOf('|');
        Set<String> out = new HashSet<>();
        if (i < 0) {
            return out;
        }
        for (String parte : assinatura.substring(i + 1).split(",")) {
            if (parte.isBlank()) {
                continue;
            }
            int j = parte.lastIndexOf(':');
            String codigo = j < 0 ? parte : parte.substring(0, j);
            if (TipoMotivo.deCodigo(codigo) != TipoMotivo.FORA_DO_HORARIO) {
                out.add(codigo);
            }
        }
        return out;
    }

    private List<Pausa> pausasSe(String merchantId, StatusOperacao st) {
        if (!st.temMotivo(TipoMotivo.PAUSA)) {
            return List.of();
        }
        try {
            return ifood.pausas(merchantId);
        } catch (RuntimeException e) {
            log.warn("Não consegui listar as pausas de {}: {}", merchantId, e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------------- lojas removidas

    /**
     * 403/404 costuma ser loja tirada das permissões do aplicativo. Antes de contar como falha,
     * confere na hora a lista de lojas do iFood (no máximo a cada 5 min); as que saíram somem do monitor sem aviso.
     */
    private Map<String, Object> semLojasRemovidas(Map<String, Object> resultados, OffsetDateTime agora) {
        boolean algum403 = resultados.values().stream()
                .anyMatch(r -> r instanceof IfoodException ie && ie.semPermissao());
        if (!algum403 || (conferidoPor403Em != null && Duration.between(conferidoPor403Em, agora).toMinutes() < 5)) {
            return resultados;
        }
        conferidoPor403Em = agora;
        try {
            atualizarLojas(true);
        } catch (RuntimeException e) {
            log.warn("Não consegui reconferir a lista de lojas: {}", e.getMessage());
            return resultados;
        }
        Set<String> ativas = new HashSet<>(merchantIds);
        Map<String, Object> out = new LinkedHashMap<>(resultados);
        out.keySet().removeIf(m -> !ativas.contains(m));
        return out;
    }

    /** Lojas que saíram do aplicativo: encerra incidentes abertos e esquece as falhas, sem mandar mensagem. */
    private void esquecerRemovidas(Set<String> ativas, OffsetDateTime agora) {
        for (Chave k : new ArrayList<>(abertos.keySet())) {
            if (!ativas.contains(k.merchantId())) {
                Incidente inc = abertos.remove(k);
                repo.fecharIncidente(inc.id, agora, "REMOVIDA");
            }
        }
        ciclosRuins.keySet().removeIf(k -> !ativas.contains(k.merchantId()));
        ruimDesde.keySet().removeIf(k -> !ativas.contains(k.merchantId()));
        falhas.keySet().removeIf(m -> !ativas.contains(m));
        falhaAvisada.removeIf(m -> !ativas.contains(m));
    }

    // ------------------------------------------------------------- falhas

    private void falhaDeLoja(String merchantId, Exception e) {
        int n = falhas.merge(merchantId, 1, Integer::sum);
        log.warn("Falha ao ler o status de {} ({}x): {}", merchantId, n, e.getMessage());
        if (n == cfg.falhasParaAvisar() && !falhaGeralAvisada) {
            String dica = e instanceof IfoodException ie && ie.semPermissao()
                    ? "\nProvável causa: a loja revogou a permissão do aplicativo ou foi desvinculada." : "";
            falhaAvisada.add(merchantId);
            enviarAdmin("⚠️ *Monitor iFood*: não consigo ler o status de *" + nomes.getOrDefault(merchantId, merchantId)
                    + "* há " + n + " ciclos.\nErro: " + e.getMessage() + dica);
        }
    }

    private void falhaGeral(Exception e) {
        if (falhaGeralAvisada) {
            return;
        }
        falhaGeralAvisada = true;
        enviarAdmin("🛑 *Monitor iFood*: não consigo ler o status de NENHUMA loja.\nErro: " + e.getMessage()
                + "\nPode ser instabilidade do iFood, credencial inválida ou falta de internet no servidor. "
                + "Os alertas de lojas ficam suspensos até normalizar.");
    }

    // ------------------------------------------------------------- envio

    private void despachar(List<Evento> eventos) {
        if (eventos.isEmpty()) {
            return;
        }
        List<Grupo> todos = grupos.listar(true);
        Map<Long, List<Evento>> porGrupo = new LinkedHashMap<>();
        Map<Long, Grupo> gruposPorId = new HashMap<>();
        for (Evento ev : eventos) {
            for (Grupo g : GrupoWhatsappRepository.gruposDaLoja(todos, ev.merchantId())) {
                if (ev.tipo() == TipoEvento.NORMALIZADO && !(cfg.avisarRecuperacao() && g.avisarRetorno())) {
                    continue;
                }
                porGrupo.computeIfAbsent(g.id(), x -> new ArrayList<>()).add(ev);
                gruposPorId.put(g.id(), g);
            }
        }
        for (Map.Entry<Long, List<Evento>> e : porGrupo.entrySet()) {
            Grupo g = gruposPorId.get(e.getKey());
            List<Long> ids = e.getValue().stream().map(ev -> ev.incidente().id).distinct().toList();
            enviar("alerta", g, Mensagens.mensagemDoGrupo(e.getValue(), zona), ids);
        }
    }

    private void enviarAdmin(String texto) {
        List<Grupo> admins = grupos.listar(true).stream().filter(Grupo::admin).toList();
        if (admins.isEmpty()) {
            log.warn("[sem grupo admin] {}", texto);
            return;
        }
        admins.forEach(g -> enviar("monitor", g, texto, List.of()));
    }

    private void enviar(String tipo, Grupo g, String texto, List<Long> incidentes) {
        OffsetDateTime agora = agora();
        if (!cfg.enviarWhatsapp()) {
            log.info("[modo sombra → {}]\n{}", g.nome(), texto);
            repo.registrarEnvio(tipo, g.nome(), g.chatId(), incidentes, texto, "SOMBRA", null, agora);
            return;
        }
        try {
            whatsapp.enviar(g.chatId(), texto);
            repo.registrarEnvio(tipo, g.nome(), g.chatId(), incidentes, texto, "ENVIADO", null, agora);
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar WhatsApp para {}: {}", g.nome(), ex.getMessage());
            repo.registrarEnvio(tipo, g.nome(), g.chatId(), incidentes, texto, "FALHOU", ex.getMessage(), agora);
        }
    }

    // ------------------------------------------------------------- resumo diário

    public synchronized boolean resumoPendente() {
        String h = cfg.resumoDiario();
        if (h == null || h.isBlank()) {
            return false;
        }
        OffsetDateTime agora = agora();
        LocalDate hoje = agora.atZoneSameInstant(zona).toLocalDate();
        OffsetDateTime alvo = hoje.atTime(LocalTime.parse(h)).atZone(zona).toOffsetDateTime();
        if (agora.isBefore(alvo)) {
            return false;
        }
        OffsetDateTime ultimo = repo.ultimoEnvio("resumo");
        return ultimo == null || ultimo.isBefore(alvo);
    }

    /** Envia a cada grupo o resumo de um dia (padrão: ontem, no fuso configurado). */
    public synchronized Map<String, String> enviarResumo(LocalDate dia) {
        carregar();
        OffsetDateTime agora = agora();
        LocalDate d = dia != null ? dia : agora.atZoneSameInstant(zona).toLocalDate().minusDays(1);
        OffsetDateTime de = d.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime ate = d.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        List<IncidenteRelatorio> todas = recortar(repo.relatorio(de, ate, null, agora), de, ate, agora);
        String rotulo = d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        List<String> lojas = merchantIds.isEmpty() ? new ArrayList<>(nomes.keySet()) : merchantIds;
        List<Grupo> todos = grupos.listar(true);
        Map<String, String> enviados = new LinkedHashMap<>();
        for (Grupo g : todos) {
            // as mesmas lojas cujos alertas vão para o grupo (inclui as sem grupo, no admin)
            Set<String> ids = lojas.stream()
                    .filter(m -> GrupoWhatsappRepository.gruposDaLoja(todos, m).stream().anyMatch(x -> x.id() == g.id()))
                    .collect(Collectors.toSet());
            if (ids.isEmpty()) {
                continue;
            }
            List<IncidenteRelatorio> doGrupo = todas.stream().filter(r -> ids.contains(r.merchantId())).toList();
            String texto = Mensagens.resumoDiario(g.nome(), doGrupo, rotulo, ids.size());
            enviar("resumo", g, texto, List.of());
            enviados.put(g.nome(), texto);
        }
        return enviados;
    }

    /** Conta só os minutos dentro do dia. */
    private static List<IncidenteRelatorio> recortar(List<IncidenteRelatorio> linhas, OffsetDateTime de,
                                                     OffsetDateTime ate, OffsetDateTime agora) {
        List<IncidenteRelatorio> out = new ArrayList<>();
        for (IncidenteRelatorio r : linhas) {
            OffsetDateTime ini = r.inicio().isBefore(de) ? de : r.inicio();
            OffsetDateTime fimReal = r.fim() != null ? r.fim() : agora;
            OffsetDateTime fim = fimReal.isAfter(ate) ? ate : fimReal;
            double min = Math.max(0, Duration.between(ini, fim).toSeconds() / 60.0);
            out.add(new IncidenteRelatorio(r.id(), r.merchantId(), r.loja(), r.operacao(), r.canal(), r.estado(),
                    r.motivos(), r.mensagem(), r.reabrivel(), r.inicio(), r.fim(), r.estadoFinal(), min, r.emAberto()));
        }
        return out;
    }

    /** Para testes e para o botão "rodar agora". */
    public synchronized List<String> lojasMonitoradas() {
        return List.copyOf(merchantIds);
    }
}
