package com.precificacao.precificacao.ifood;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Núcleo das chamadas ao iFood, usado pelos clientes de Merchant e Catalog.
 *
 * - Token client_credentials guardado em memória e renovado só perto de expirar
 *   (o iFood pode bloquear aplicativos que pedem token demais).
 * - 401: renova o token uma vez e repete.
 * - 429 e 5xx: espera (Retry-After ou backoff exponencial) e repete, até maxTentativas.
 * - Thread-safe: o monitor consulta várias lojas em paralelo com a mesma instância.
 */
@Component
public class IfoodHttp {

    private static final Logger log = LoggerFactory.getLogger(IfoodHttp.class);
    private static final String CAMINHO_TOKEN = "/authentication/v1.0/oauth/token";

    /** Permite trocar a espera real por uma falsa nos testes. */
    @FunctionalInterface
    public interface Espera {
        void esperar(long millis) throws InterruptedException;
    }

    private final IfoodProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final Clock clock;
    private Espera espera = Thread::sleep;

    private final Object travaToken = new Object();
    private volatile String token;
    private volatile Instant renovarTokenEm = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public IfoodHttp(IfoodProperties props, ObjectMapper mapper) {
        this(props, mapper, Clock.systemUTC());
    }

    public IfoodHttp(IfoodProperties props, ObjectMapper mapper, Clock clock) {
        this.props = props;
        this.mapper = mapper;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setEspera(Espera espera) {
        this.espera = espera;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    // ------------------------------------------------------------------ token

    public String token(boolean forcar) {
        synchronized (travaToken) {
            if (forcar || token == null || !clock.instant().isBefore(renovarTokenEm)) {
                buscarToken();
            }
            return token;
        }
    }

    /** Chame quando uma loja nova autorizar o aplicativo: o token precisa ser refeito para incluí-la. */
    public void invalidarToken() {
        synchronized (travaToken) {
            renovarTokenEm = Instant.EPOCH;
        }
    }

    private void buscarToken() {
        if (!props.configurado()) {
            throw new IfoodException(0, "Credenciais do iFood não configuradas (IFOOD_CLIENT_ID e IFOOD_CLIENT_SECRET)", null);
        }
        String form = "grantType=client_credentials"
                + "&clientId=" + enc(props.clientId())
                + "&clientSecret=" + enc(props.clientSecret());
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.baseUrlSemBarra() + CAMINHO_TOKEN))
                .timeout(Duration.ofSeconds(props.timeoutSegundos()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IfoodException("Falha de rede ao obter token do iFood: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IfoodException("Interrompido ao obter token do iFood", e);
        }
        if (resp.statusCode() != 200) {
            throw new IfoodException(resp.statusCode(), "falha ao obter token (confira clientId/clientSecret)", resp.body());
        }
        JsonNode body = ler(resp.body());
        String novo = body.path("accessToken").asText(null);
        if (novo == null || novo.isBlank()) {
            throw new IfoodException(resp.statusCode(), "resposta de token sem accessToken", resp.body());
        }
        long expira = body.path("expiresIn").asLong(3600);
        // renova 5 min antes de expirar (ou na metade, se o token for curto)
        long margem = Math.min(300, expira / 2);
        token = novo;
        renovarTokenEm = clock.instant().plusSeconds(expira - margem);
        log.info("Token iFood obtido; válido por {}s", expira);
    }

    // --------------------------------------------------------------- chamadas

    public JsonNode get(String caminho) {
        return enviar("GET", caminho, null, null);
    }

    public JsonNode get(String caminho, Map<String, ?> query) {
        return enviar("GET", caminho, query, null);
    }

    public JsonNode post(String caminho, Object corpo) {
        return enviar("POST", caminho, null, corpo);
    }

    public JsonNode put(String caminho, Object corpo) {
        return enviar("PUT", caminho, null, corpo);
    }

    public JsonNode patch(String caminho, Object corpo) {
        return enviar("PATCH", caminho, null, corpo);
    }

    public JsonNode delete(String caminho) {
        return enviar("DELETE", caminho, null, null);
    }

    /**
     * Faz a chamada com renovação de token e novas tentativas.
     * Devolve o JSON da resposta; corpo vazio vira NullNode e texto puro
     * (ex.: a versão do catálogo, "v2") vira TextNode.
     */
    public JsonNode enviar(String metodo, String caminho, Map<String, ?> query, Object corpo) {
        String url = props.baseUrlSemBarra() + caminho + montarQuery(query);
        String json = corpo == null ? null : escrever(corpo);
        boolean renovouToken = false;
        int tentativa = 0;
        while (true) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(props.timeoutSegundos()))
                    .header("Authorization", "Bearer " + token(false))
                    .header("Accept", "application/json");
            if (json != null) {
                b.header("Content-Type", "application/json");
                b.method(metodo, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else {
                b.method(metodo, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> resp;
            try {
                resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (tentativa >= props.maxTentativas()) {
                    throw new IfoodException("Falha de rede em " + metodo + " " + caminho + ": " + e.getMessage(), e);
                }
                aguardar(tentativa++, null);
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IfoodException("Interrompido em " + metodo + " " + caminho, e);
            }

            int status = resp.statusCode();
            if (status == 401 && !renovouToken) {
                renovouToken = true;
                token(true);
                continue;
            }
            if (status == 429 || status >= 500) {
                if (tentativa >= props.maxTentativas()) {
                    throw new IfoodException(status, "limite de tentativas esgotado em " + metodo + " " + caminho, resp.body());
                }
                aguardar(tentativa++, resp.headers().firstValue("Retry-After").orElse(null));
                continue;
            }
            if (status < 200 || status >= 300) {
                throw new IfoodException(status, metodo + " " + caminho, resp.body());
            }
            return ler(resp.body());
        }
    }

    private void aguardar(int tentativa, String retryAfter) {
        long ms;
        if (retryAfter != null) {
            try {
                ms = (long) (Math.min(Double.parseDouble(retryAfter.trim()), 60) * 1000);
            } catch (NumberFormatException e) {
                ms = backoff(tentativa);
            }
        } else {
            ms = backoff(tentativa);
        }
        try {
            espera.esperar(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IfoodException("Interrompido enquanto aguardava o iFood", e);
        }
    }

    private static long backoff(int tentativa) {
        long base = Math.min(1L << tentativa, 30) * 1000;
        return base + ThreadLocalRandom.current().nextLong(500);
    }

    // ---------------------------------------------------------------- apoio

    public JsonNode ler(String corpo) {
        if (corpo == null || corpo.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return mapper.readTree(corpo);
        } catch (JsonProcessingException e) {
            return TextNode.valueOf(corpo.trim()); // texto puro, ex.: "v2"
        }
    }

    private String escrever(Object corpo) {
        if (corpo instanceof String s) {
            return s;
        }
        try {
            return mapper.writeValueAsString(corpo);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Corpo inválido para o iFood", e);
        }
    }

    private static String montarQuery(Map<String, ?> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringJoiner j = new StringJoiner("&", "?", "");
        new LinkedHashMap<>(query).forEach((k, v) -> {
            if (v != null) {
                j.add(enc(k) + "=" + enc(String.valueOf(v)));
            }
        });
        return j.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
