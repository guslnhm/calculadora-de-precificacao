package com.precificacao.precificacao.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda o monitor de tempos em tempos (MONITOR_ATIVO=true para ligar).
 * Um ciclo com erro nunca derruba o agendamento: o erro vai para o log e o próximo ciclo tenta de novo.
 */
@Component
public class MonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);

    private final MonitorService monitor;
    private final MonitorProperties cfg;

    public MonitorScheduler(MonitorService monitor, MonitorProperties cfg) {
        this.monitor = monitor;
        this.cfg = cfg;
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "#{${monitor.intervalo-segundos:30} * 1000}")
    public void executar() {
        if (!cfg.ativo()) {
            return;
        }
        long t0 = System.currentTimeMillis();
        try {
            monitor.ciclo();
            if (monitor.resumoPendente()) {
                monitor.enviarResumo(null);
            }
        } catch (RuntimeException e) {
            log.error("Erro inesperado no ciclo do monitor", e);
        }
        long ms = System.currentTimeMillis() - t0;
        if (ms > cfg.intervaloSegundos() * 1000L) {
            log.warn("Ciclo do monitor levou {} ms (mais que o intervalo). Aumente MONITOR_WORKERS.", ms);
        }
    }
}
