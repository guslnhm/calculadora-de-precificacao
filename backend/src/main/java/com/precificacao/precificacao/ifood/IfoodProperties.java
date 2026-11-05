package com.precificacao.precificacao.ifood;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.ZoneId;

/**
 * Configuração da integração com o iFood (seção "ifood" do application.yaml).
 *
 * client-id e client-secret vêm das variáveis de ambiente IFOOD_CLIENT_ID e
 * IFOOD_CLIENT_SECRET (aplicativo CENTRALIZADO). Sem elas, o sistema sobe
 * normalmente e só as chamadas ao iFood falham.
 */
@ConfigurationProperties(prefix = "ifood")
public record IfoodProperties(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        @DefaultValue("https://merchant-api.ifood.com.br") String baseUrl,
        @DefaultValue("America/Sao_Paulo") String fuso,
        @DefaultValue("15") int timeoutSegundos,
        @DefaultValue("4") int maxTentativas
) {

    public boolean configurado() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    public ZoneId zona() {
        return ZoneId.of(fuso);
    }

    public String baseUrlSemBarra() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
