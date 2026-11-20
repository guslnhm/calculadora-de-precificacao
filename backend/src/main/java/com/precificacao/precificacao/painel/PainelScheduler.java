package com.precificacao.precificacao.painel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agenda do painel. As tarefas rodam numa thread própria para não atrasar o monitor de lojas,
 * que usa a thread padrão do @Scheduled.
 *  - 09:40 todo dia: coleta do Analytics (o iFood diz liberar o D-1 até 9:00, mas libera aos poucos).
 *  - 10:00 a 13:40, a cada 20 min: relê o dia de ontem até ele parar de mudar (ver ResumoDiarioService).
 *  - segunda 10:30: reimporta o histórico do BI antigo (enquanto o Power BI ainda é alimentado).
 *  - ao subir: importa o BI se ainda não há nada importado.
 */
@Component
public class PainelScheduler {

    private static final Logger log = LoggerFactory.getLogger(PainelScheduler.class);

    private final PainelColetaService coleta;
    private final BiImportService bi;
    private final PainelRepository repo;
    private final PainelProperties cfg;
    private final ResumoDiarioService resumo;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "painel");
        t.setDaemon(true);
        return t;
    });

    public PainelScheduler(PainelColetaService coleta, BiImportService bi, PainelRepository repo, PainelProperties cfg,
                           ResumoDiarioService resumo) {
        this.resumo = resumo;
        this.coleta = coleta;
        this.bi = bi;
        this.repo = repo;
        this.cfg = cfg;
    }

    @Scheduled(cron = "0 40 9 * * *", zone = "America/Sao_Paulo")
    public void coletaDiaria() {
        if (cfg.coletaAtiva() && cfg.analyticsConfigurado()) {
            dispararColeta();
        }
    }

    /**
     * O iFood libera o dia anterior aos poucos depois das 9h (loja zerada, ou só parte dos pedidos).
     * A cada 20 min, das 10h às 13h40, relê só o dia de ontem de todas as lojas, enquanto o resumo estiver
     * esperando os números ou puder precisar de correção.
     */
    @Scheduled(cron = "0 0/20 10-13 * * *", zone = "America/Sao_Paulo")
    public void relerOntem() {
        if (!cfg.coletaAtiva() || !cfg.analyticsConfigurado() || coleta.rodando() || !resumo.aguardandoOntem()) {
            return;
        }
        java.time.LocalDate ontem = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusDays(1);
        if (repo.pedidosDoDia(ontem) == 0 && !coleta.estavel(ontem)) {
            // nada de ontem ainda: coleta completa (relê também os dias anteriores)
            dispararColeta();
        } else {
            disparar(() -> coleta.recoletarDia(ontem));
        }
    }

    @Scheduled(cron = "0 30 10 * * MON", zone = "America/Sao_Paulo")
    public void importacaoSemanal() {
        if (cfg.bancoBiConfigurado()) {
            disparar(bi::importar);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        if (cfg.bancoBiConfigurado() && repo.biImportadoEm() == null) {
            disparar(bi::importar);
        }
    }

    /** Coleta em segundo plano. Devolve false se já havia uma rodando. */
    public boolean dispararColeta() {
        if (coleta.rodando()) {
            return false;
        }
        disparar(() -> {
            coleta.coletarTodas();
            bi.revincular();
        });
        return true;
    }

    public void disparar(Runnable r) {
        executor.submit(() -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                log.warn("[painel] tarefa falhou", e);
            }
        });
    }
}
