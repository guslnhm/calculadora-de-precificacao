package com.precificacao.precificacao.food99;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Chamadas HTTP reais à 99Food. IDs de 64 bits são lidos sem arredondar (BigInteger/long). */
@Component
public class Food99Http implements Food99Api {

    private final Food99Properties props;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();

    public Food99Http(Food99Properties props) {
        this.props = props;
    }

    @Override
    public JsonNode post(String caminho, Map<String, Object> corpo, String bearer) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(props.baseUrlSemBarra() + caminho))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpo), StandardCharsets.UTF_8));
            if (bearer != null) {
                b.header("Authorization", "Bearer " + bearer);
            }
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() / 100 != 2) {
                throw new Food99Exception(r.statusCode(), "99Food respondeu HTTP " + r.statusCode() + " em " + caminho
                        + ": " + resumo(r.body()));
            }
            return JSON.readTree(r.body());
        } catch (IOException e) {
            throw new Food99Exception("Falha de rede ao chamar a 99Food (" + caminho + "): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Food99Exception("Interrompido ao chamar a 99Food", e);
        }
    }

    private static String resumo(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
