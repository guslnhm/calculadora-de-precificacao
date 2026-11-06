package com.precificacao.precificacao.cardapio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Relê os cardápios de tempos em tempos (MONITOR_ITENS_INTERVALO_MINUTOS, padrão 10). */
@Component
public class MonitorItensScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorItensScheduler.class);

    private final MonitorItensService monitor;

    public MonitorItensScheduler(MonitorItensService monitor) {
        this.monitor = monitor;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "#{${monitor.itens.intervalo-minutos:10} * 60000}")
    public void executar() {
        if (!monitor.ligado()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            var m = monitor.ciclo();
            log.info("Monitor de itens: ciclo em {} ms, {} loja(s) com novidade", System.currentTimeMillis() - t0, m.size());
        } catch (RuntimeException e) {
            log.error("Erro inesperado no monitor de itens", e);
        }
    }
}
