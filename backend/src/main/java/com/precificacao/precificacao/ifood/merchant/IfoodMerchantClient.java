package com.precificacao.precificacao.ifood.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.precificacao.precificacao.ifood.IfoodHttp;
import com.precificacao.precificacao.ifood.IfoodProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Módulo Merchant do iFood: lojas, status, pausas e horário de funcionamento. */
@Component
public class IfoodMerchantClient {

    static final String BASE = "/merchant/v1.0";
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IfoodHttp http;
    private final IfoodProperties props;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public IfoodMerchantClient(IfoodHttp http, IfoodProperties props) {
        this(http, props, Clock.systemUTC());
    }

    public IfoodMerchantClient(IfoodHttp http, IfoodProperties props, Clock clock) {
        this.http = http;
        this.props = props;
        this.clock = clock;
    }

    // ------------------------------------------------------------- lojas

    /** Todas as lojas que autorizaram o aplicativo (pagina de 100 em 100). */
    public List<JsonNode> listarLojas() {
        List<JsonNode> out = new ArrayList<>();
        int pagina = 1;
        final int tamanho = 100;
        while (true) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("page", pagina);
            q.put("size", tamanho);
            JsonNode lote = http.get(BASE + "/merchants", q);
            int n = 0;
            if (lote.isArray()) {
                for (JsonNode m : lote) {
                    out.add(m);
                    n++;
                }
            }
            if (n < tamanho) {
                return out;
            }
            pagina++;
        }
    }

    public JsonNode detalhesLoja(String merchantId) {
        return http.get(BASE + "/merchants/" + merchantId);
    }

    public List<StatusOperacao> status(String merchantId) {
        return StatusParser.ler(http.get(BASE + "/merchants/" + merchantId + "/status"));
    }

    // ------------------------------------------------------------- pausas

    public JsonNode listarPausas(String merchantId) {
        return http.get(BASE + "/merchants/" + merchantId + "/interruptions");
    }

    /**
     * Cria uma pausa. O iFood interpreta o horário no fuso da loja e descarta o fuso
     * enviado, então os horários vão como hora local, sem offset.
     */
    public JsonNode criarPausa(String merchantId, String descricao, LocalDateTime inicio, LocalDateTime fim) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("id", UUID.randomUUID().toString());
        corpo.put("description", descricao);
        corpo.put("start", inicio.format(LOCAL));
        corpo.put("end", fim.format(LOCAL));
        return http.post(BASE + "/merchants/" + merchantId + "/interruptions", corpo);
    }

    /** Pausa a partir de agora (+5 s) por alguns minutos, no fuso configurado. */
    public JsonNode pausarPor(String merchantId, int minutos, String descricao) {
        LocalDateTime inicio = ZonedDateTime.now(clock).withZoneSameInstant(props.zona()).toLocalDateTime().plusSeconds(5);
        return criarPausa(merchantId, descricao, inicio, inicio.plusMinutes(minutos));
    }

    public void removerPausa(String merchantId, String pausaId) {
        http.delete(BASE + "/merchants/" + merchantId + "/interruptions/" + pausaId);
    }

    // ------------------------------------------------------------- horários

    public JsonNode horarios(String merchantId) {
        return http.get(BASE + "/merchants/" + merchantId + "/opening-hours");
    }

    /** Substitui TODOS os horários da loja: dias que não forem enviados ficam fechados. */
    public JsonNode definirHorarios(String merchantId, List<Turno> turnos) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("storeId", merchantId);
        corpo.put("shifts", turnos);
        return http.put(BASE + "/merchants/" + merchantId + "/opening-hours", corpo);
    }
}
