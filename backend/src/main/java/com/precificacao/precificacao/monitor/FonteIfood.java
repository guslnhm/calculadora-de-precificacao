package com.precificacao.precificacao.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.precificacao.precificacao.ifood.IfoodHttp;
import com.precificacao.precificacao.ifood.merchant.IfoodMerchantClient;
import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorModelos.Pausa;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * O que o monitor precisa do iFood. Fica atrás desta interface para os testes
 * poderem simular as lojas sem chamar o iFood.
 */
public interface FonteIfood {

    List<LojaMerchant> listarLojas();

    List<StatusOperacao> status(String merchantId);

    List<Pausa> pausas(String merchantId);

    /** Refaz o token na próxima chamada (pega lojas recém-autorizadas). */
    void renovarAcesso();

    @Component
    class ViaApi implements FonteIfood {

        private final IfoodMerchantClient merchant;
        private final IfoodHttp http;

        public ViaApi(IfoodMerchantClient merchant, IfoodHttp http) {
            this.merchant = merchant;
            this.http = http;
        }

        @Override
        public List<LojaMerchant> listarLojas() {
            List<LojaMerchant> out = new ArrayList<>();
            for (JsonNode m : merchant.listarLojas()) {
                out.add(new LojaMerchant(m.path("id").asText(), m.path("name").asText(null), m.path("corporateName").asText(null)));
            }
            return out;
        }

        @Override
        public List<StatusOperacao> status(String merchantId) {
            return merchant.status(merchantId);
        }

        @Override
        public List<Pausa> pausas(String merchantId) {
            List<Pausa> out = new ArrayList<>();
            for (JsonNode p : merchant.listarPausas(merchantId)) {
                out.add(new Pausa(p.path("id").asText(null), p.path("description").asText(null),
                        p.path("start").asText(null), p.path("end").asText(null)));
            }
            return out;
        }

        @Override
        public void renovarAcesso() {
            http.invalidarToken();
        }
    }
}
