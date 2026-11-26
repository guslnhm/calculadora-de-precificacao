package com.precificacao.precificacao.food99;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Coleta diária do financeiro da 99Food (11:10), numa thread própria. Só roda com as credenciais configuradas. */
@Component
public class Food99Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Food99Scheduler.class);

    private final Food99Service service;
    private final Food99Properties props;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "food99");
        t.setDaemon(true);
        return t;
    });

    public Food99Scheduler(Food99Service service, Food99Properties props) {
        this.service = service;
        this.props = props;
    }

    @Scheduled(cron = "0 10 11 * * *", zone = "America/Sao_Paulo")
    public void coletaDiaria() {
        if (props.coletaAtiva() && props.configurado()) {
            disparar(java.time.LocalDate.MIN, null);
        }
    }

    /** Coleta em segundo plano; de == MIN: os últimos dias. Devolve false se já havia uma rodando. */
    public boolean disparar(java.time.LocalDate de, java.time.LocalDate ate) {
        if (service.rodando()) {
            return false;
        }
        executor.submit(() -> {
            try {
                Food99Service.Resultado r = de == java.time.LocalDate.MIN ? service.coletarRecentes() : service.coletar(de, ate);
                log.info("99Food: {} loja(s), {} lançamento(s), {} erro(s)", r.lojas(), r.lancamentos(), r.erros().size());
            } catch (RuntimeException e) {
                log.warn("99Food: coleta falhou: {}", e.getMessage());
            }
        });
        return true;
    }
}
