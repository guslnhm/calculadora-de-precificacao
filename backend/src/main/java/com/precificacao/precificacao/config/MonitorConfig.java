package com.precificacao.precificacao.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.monitor.MonitorProperties;
import com.precificacao.precificacao.whatsapp.ConsoleSender;
import com.precificacao.precificacao.whatsapp.EvolutionSender;
import com.precificacao.precificacao.whatsapp.WhatsappProperties;
import com.precificacao.precificacao.whatsapp.WhatsappSender;
import com.precificacao.precificacao.whatsapp.ZapiSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({MonitorProperties.class, WhatsappProperties.class})
public class MonitorConfig {

    private static final Logger log = LoggerFactory.getLogger(MonitorConfig.class);

    @Bean
    public WhatsappSender whatsappSender(WhatsappProperties props, ObjectMapper mapper) {
        if ("evolution".equalsIgnoreCase(props.provider())) {
            if (!props.evolutionConfigurada()) {
                log.warn("WHATSAPP_PROVIDER=evolution, mas EVOLUTION_URL/EVOLUTION_API_KEY não estão configurados: mensagens só vão para o log");
                return new ConsoleSender();
            }
            return new EvolutionSender(props, mapper);
        }
        if ("zapi".equalsIgnoreCase(props.provider())) {
            if (!props.zapiConfigurada()) {
                log.warn("WHATSAPP_PROVIDER=zapi, mas ZAPI_INSTANCE_ID/ZAPI_TOKEN não estão configurados: mensagens só vão para o log");
                return new ConsoleSender();
            }
            return new ZapiSender(props, mapper);
        }
        return new ConsoleSender();
    }

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
