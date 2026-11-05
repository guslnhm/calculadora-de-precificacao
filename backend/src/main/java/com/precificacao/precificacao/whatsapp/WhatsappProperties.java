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
    public WhatsappProperties {
        provider = limpar(provider);
        zapiInstanceId = limpar(zapiInstanceId);
        zapiToken = limpar(zapiToken);
        zapiClientToken = limpar(zapiClientToken);
        zapiBaseUrl = limpar(zapiBaseUrl).isEmpty() ? "https://api.z-api.io" : limpar(zapiBaseUrl);
    }

    /** Tira espaços, quebras de linha e aspas que costumam vir junto ao colar a variável no Railway. */
    static String limpar(String v) {
        if (v == null) {
            return "";
        }
        String s = v.strip();
        while (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).strip();
        }
        return s;
    }

    public boolean zapiConfigurada() {
        return !zapiInstanceId.isBlank() && !zapiToken.isBlank();
    }
}
