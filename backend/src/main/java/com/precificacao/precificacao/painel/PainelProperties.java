package com.precificacao.precificacao.painel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuração do painel (seção "painel" do application.yaml).
 *
 * @param biClientId      clientId do app de BI no iFood (módulo Analytics) — IFOOD_BI_CLIENT_ID
 * @param biClientSecret  clientSecret do app de BI — IFOOD_BI_CLIENT_SECRET
 * @param biDatabaseUrl   banco do Power BI antigo, só leitura (BI_DATABASE_URL), para importar o histórico
 * @param coletaAtiva     liga a coleta diária do Analytics
 * @param diasReprocessar a cada coleta, relê os últimos N dias (pedido concluído pode ser cancelado depois)
 * @param diasHistorico   na 1ª coleta de uma loja, busca N dias para trás
 * @param semanasCurva    semanas usadas na curva de venda por hora (base das perdas)
 */
@ConfigurationProperties(prefix = "painel")
public record PainelProperties(
        @DefaultValue("") String biClientId,
        @DefaultValue("") String biClientSecret,
        @DefaultValue("") String biDatabaseUrl,
        @DefaultValue("true") boolean coletaAtiva,
        @DefaultValue("15") int diasReprocessar,
        @DefaultValue("180") int diasHistorico,
        @DefaultValue("8") int semanasCurva
) {
    public boolean analyticsConfigurado() {
        return biClientId != null && !biClientId.isBlank() && biClientSecret != null && !biClientSecret.isBlank();
    }

    public boolean bancoBiConfigurado() {
        return biDatabaseUrl != null && !biDatabaseUrl.isBlank();
    }
}
