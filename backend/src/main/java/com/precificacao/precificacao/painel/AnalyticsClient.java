package com.precificacao.precificacao.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.IfoodHttp;
import com.precificacao.precificacao.ifood.IfoodProperties;
import org.springframework.stereotype.Component;

/**
 * Chamadas ao módulo Analytics com as credenciais do app de BI (separadas das do app RIZQ).
 * Usa um IfoodHttp próprio, fora do Spring, para não misturar os tokens.
 */
@Component
public class AnalyticsClient implements LeitorKpis.Fonte {

    private final PainelProperties props;
    private final IfoodHttp http;

    public AnalyticsClient(PainelProperties props, IfoodProperties ifood, ObjectMapper mapper) {
        this.props = props;
        this.http = new IfoodHttp(new IfoodProperties(props.biClientId(), props.biClientSecret(), ifood.baseUrl(),
                ifood.fuso(), 60, 4), mapper);
    }

    public boolean configurado() {
        return props.analyticsConfigurado();
    }

    @Override
    public JsonNode kpis(String merchantId, JsonNode corpo) {
        if (!configurado()) {
            throw new IfoodException(0, "Credenciais do app de BI não configuradas (IFOOD_BI_CLIENT_ID e IFOOD_BI_CLIENT_SECRET)", null);
        }
        return http.post("/analytics/v1.0/merchants/" + merchantId + "/orders/kpis", corpo);
    }
}
