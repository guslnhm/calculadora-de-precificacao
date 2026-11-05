package com.precificacao.precificacao.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Envio de mensagens de WhatsApp (seção "whatsapp" do application.yaml).
 *
 * provider: zapi (Z-API, número comum pareado por QR Code) ou console (só registra no log).
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsappProperties(
        @DefaultValue("console") String provider,
        @DefaultValue("") String zapiInstanceId,
        @DefaultValue("") String zapiToken,
        @DefaultValue("") String zapiClientToken,
        @DefaultValue("https://api.z-api.io") String zapiBaseUrl
) {
    public boolean zapiConfigurada() {
        return !zapiInstanceId.isBlank() && !zapiToken.isBlank();
    }
}
