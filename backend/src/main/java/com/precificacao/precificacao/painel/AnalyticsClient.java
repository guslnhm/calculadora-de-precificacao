package com.precificacao.precificacao.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.IfoodHttp;
import com.precificacao.precificacao.ifood.IfoodProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Chamadas ao módulo Analytics com as credenciais do app de BI (separadas das do app RIZQ).
 * Usa um IfoodHttp próprio, fora do Spring, para não misturar os tokens.
 *
 * O token do iFood só inclui as lojas que já tinham autorizado o app quando ele foi emitido. Por isso, num 403
 * o token é refeito e a chamada repetida uma vez (no máximo a cada 15 min, para não pedir token demais).
 */
@Component
public class AnalyticsClient implements LeitorKpis.Fonte {

    private final PainelProperties props;
    private final IfoodHttp http;
    static final Duration INTERVALO_RENOVACAO = Duration.ofMinutes(15);
    private volatile Instant tokenRefeitoEm = Instant.EPOCH;

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
        String caminho = "/analytics/v1.0/merchants/" + merchantId + "/orders/kpis";
        try {
            return http.post(caminho, corpo);
        } catch (IfoodException e) {
            if (!e.semPermissao() || !podeRefazerToken()) {
                throw e;
            }
            http.invalidarToken();
            return http.post(caminho, corpo);
        }
    }

    private synchronized boolean podeRefazerToken() {
        Instant agora = Instant.now();
        if (tokenRefeitoEm.plus(INTERVALO_RENOVACAO).isAfter(agora)) {
            return false;
        }
        tokenRefeitoEm = agora;
        return true;
    }
}
