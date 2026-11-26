package com.precificacao.precificacao.food99;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 99Food Open Platform (seção "food99" do application.yaml).
 *
 * @param appId          app_id do app na 99 (FOOD99_APP_ID)
 * @param appSecret      app_secret (FOOD99_APP_SECRET) — só nas variáveis do Railway
 * @param baseUrl        endereço da API
 * @param coletaAtiva    liga a coleta diária do financeiro
 * @param diasReler      a cada coleta, relê os últimos N dias (a 99 libera com até 2 dias de atraso)
 * @param divisorValores os valores vêm em inteiros; 100 = centavos (conferir com o Portal na loja teste)
 */
@ConfigurationProperties(prefix = "food99")
public record Food99Properties(
        @DefaultValue("") String appId,
        @DefaultValue("") String appSecret,
        @DefaultValue("https://openapi.99food.com") String baseUrl,
        @DefaultValue("true") boolean coletaAtiva,
        @DefaultValue("10") int diasReler,
        @DefaultValue("100") int divisorValores
) {
    public boolean configurado() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    public String baseUrlSemBarra() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
