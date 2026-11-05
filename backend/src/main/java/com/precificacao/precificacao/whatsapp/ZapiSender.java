package com.precificacao.precificacao.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Z-API: conecta um número de WhatsApp comum (via QR Code) e envia para os grupos
 * em que esse número participa. Para grupo, o "phone" é o ID do grupo (ex.: 1203…-group).
 */
public class ZapiSender implements WhatsappSender {

    private final String base;
    private final String clientToken;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ZapiSender(WhatsappProperties props, ObjectMapper mapper) {
        String url = props.zapiBaseUrl().endsWith("/") ? props.zapiBaseUrl().substring(0, props.zapiBaseUrl().length() - 1) : props.zapiBaseUrl();
        this.base = url + "/instances/" + props.zapiInstanceId() + "/token/" + props.zapiToken();
        this.clientToken = props.zapiClientToken();
        this.mapper = mapper;
    }

    @Override
    public void enviar(String chatId, String texto) {
        Map<String, String> corpo = new LinkedHashMap<>();
        corpo.put("phone", chatId);
        corpo.put("message", texto);
        String erro = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            try {
                HttpResponse<String> r = http.send(requisicao("/send-text")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(corpo), StandardCharsets.UTF_8))
                        .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (r.statusCode() < 300) {
                    return;
                }
                erro = "Z-API respondeu HTTP " + r.statusCode() + ": " + corta(r.body());
                if (r.statusCode() < 500 && r.statusCode() != 429) {
                    break; // erro do pedido (ex.: credencial): não adianta repetir
                }
            } catch (IOException e) {
                erro = "Falha de rede com a Z-API: " + e.getMessage();
            } catch (IllegalArgumentException e) {
                throw new FalhaEnvio("Endereço da Z-API inválido: confira ZAPI_INSTANCE_ID e ZAPI_TOKEN no Railway ("
                        + e.getMessage() + ")", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FalhaEnvio("Interrompido ao enviar WhatsApp", e);
            }
            try {
                Thread.sleep(2000L * (tentativa + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new FalhaEnvio(erro == null ? "falha desconhecida" : erro);
    }

    @Override
    public List<GrupoWhatsapp> listarGrupos() {
        List<GrupoWhatsapp> out = new ArrayList<>();
        List<JsonNode> chats;
        try {
            chats = paginado("/groups");
        } catch (FalhaEnvio e) {
            chats = paginado("/chats"); // alternativa: todos os chats, filtrando os grupos
        }
        for (JsonNode c : chats) {
            if (c.path("isGroup").asBoolean(true)) {
                out.add(new GrupoWhatsapp(c.path("phone").asText(), c.path("name").asText()));
            }
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> situacao() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "zapi");
        try {
            m.putAll(mapper.convertValue(get("/status", ""), Map.class));
        } catch (RuntimeException e) {
            m.put("erro", e.getMessage());
        }
        return m;
    }

    private List<JsonNode> paginado(String caminho) {
        List<JsonNode> out = new ArrayList<>();
        for (int pagina = 1; pagina <= 50; pagina++) {
            JsonNode lote = get(caminho, "?page=" + pagina + "&pageSize=100");
            int n = 0;
            for (JsonNode c : lote) {
                out.add(c);
                n++;
            }
            if (n < 100) {
                break;
            }
        }
        return out;
    }

    private JsonNode get(String caminho, String query) {
        try {
            HttpResponse<String> r = http.send(requisicao(caminho + query).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() >= 400) {
                throw new FalhaEnvio("Z-API respondeu HTTP " + r.statusCode() + " em " + caminho + ": " + corta(r.body()));
            }
            return r.body() == null || r.body().isBlank() ? mapper.createArrayNode() : mapper.readTree(r.body());
        } catch (IOException e) {
            throw new FalhaEnvio("Falha de rede com a Z-API: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new FalhaEnvio("Endereço da Z-API inválido: confira ZAPI_INSTANCE_ID e ZAPI_TOKEN no Railway ("
                    + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaEnvio("Interrompido", e);
        }
    }

    private HttpRequest.Builder requisicao(String caminho) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (clientToken != null && !clientToken.isBlank()) {
            b.header("Client-Token", clientToken); // token de segurança da conta (opcional)
        }
        return b;
    }

    private static String corta(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s;
    }
}
