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
 *  - 09:40 todo dia: coleta do Analytics (o iFood libera o D-1 até 9:00).
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "painel");
        t.setDaemon(true);
        return t;
    });

    public PainelScheduler(PainelColetaService coleta, BiImportService bi, PainelRepository repo, PainelProperties cfg) {
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
