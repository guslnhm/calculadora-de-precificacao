package com.precificacao.precificacao.monitor;

import com.precificacao.precificacao.monitor.MonitorModelos.Grupo;
import com.precificacao.precificacao.whatsapp.WhatsappSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/** Envia (ou só registra, no modo sombra) uma mensagem para um grupo e guarda em whatsapp_envio. */
@Component
public class EnvioWhatsapp {

    private static final Logger log = LoggerFactory.getLogger(EnvioWhatsapp.class);

    private final MonitorProperties cfg;
    private final WhatsappSender whatsapp;
    private final MonitorRepository repo;
    private final Clock clock;

    public EnvioWhatsapp(MonitorProperties cfg, WhatsappSender whatsapp, MonitorRepository repo, Clock clock) {
        this.cfg = cfg;
        this.whatsapp = whatsapp;
        this.repo = repo;
        this.clock = clock;
    }

    /** Devolve a situação registrada: ENVIADO, SOMBRA ou FALHOU. */
    public String enviar(String tipo, Grupo g, String texto) {
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (!cfg.enviarWhatsapp()) {
            log.info("[modo sombra → {}]\n{}", g.nome(), texto);
            repo.registrarEnvio(tipo, g.nome(), g.chatId(), List.of(), texto, "SOMBRA", null, agora);
            return "SOMBRA";
        }
        try {
            whatsapp.enviar(g.chatId(), texto);
            repo.registrarEnvio(tipo, g.nome(), g.chatId(), List.of(), texto, "ENVIADO", null, agora);
            return "ENVIADO";
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar WhatsApp para {}: {}", g.nome(), ex.getMessage());
            repo.registrarEnvio(tipo, g.nome(), g.chatId(), List.of(), texto, "FALHOU", ex.getMessage(), agora);
            return "FALHOU";
        }
    }
}
