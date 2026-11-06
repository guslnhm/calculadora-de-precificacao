package com.precificacao.precificacao.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
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
 * Evolution API (v2), rodando como serviço próprio no Railway: conecta o número de WhatsApp por QR Code
 * e envia para os grupos em que ele participa. Grupo = "1203…@g.us" (também aceita o formato da Z-API, "1203…-group").
 */
public class EvolutionSender implements WhatsappSender {

    private final String base;
    private final String apiKey;
    private final String instancia;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    record Resposta(int status, String corpo) {
        boolean ok() {
            return status < 300;
        }
    }

    public EvolutionSender(WhatsappProperties props, ObjectMapper mapper) {
        this.base = props.evolutionUrl();
        this.apiKey = props.evolutionApiKey();
        this.instancia = URLEncoder.encode(props.evolutionInstancia(), StandardCharsets.UTF_8).replace("+", "%20");
        this.mapper = mapper;
    }

    /** "1203…-group" (Z-API) ou "1203…" viram "1203…@g.us"; números e JIDs completos passam como estão. */
    static String jid(String chatId) {
        String id = chatId == null ? "" : chatId.strip();
        if (id.endsWith("-group")) {
            return id.substring(0, id.length() - "-group".length()) + "@g.us";
        }
        if (!id.contains("@") && id.matches("\\d{15,}")) {
            return id + "@g.us"; // IDs de grupo são longos; telefones têm até 13 dígitos
        }
        return id;
    }

    @Override
    public void enviar(String chatId, String texto) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("number", jid(chatId));
        corpo.put("text", texto);
        String erro = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            try {
                Resposta r = chamar("POST", "/message/sendText/" + instancia, mapper.writeValueAsString(corpo));
                if (r.ok()) {
                    return;
                }
                erro = "Evolution respondeu HTTP " + r.status() + ": " + corta(r.corpo());
                if (r.status() < 500 && r.status() != 429) {
                    break; // erro do pedido (grupo inválido, número desconectado…): não adianta repetir
                }
            } catch (IOException e) {
                erro = "Falha de rede com a Evolution: " + e.getMessage();
            }
            dormir(2000L * (tentativa + 1));
        }
        throw new FalhaEnvio(erro == null ? "falha desconhecida" : erro);
    }

    @Override
    public List<GrupoWhatsapp> listarGrupos() {
        JsonNode grupos = json(exigir(get("/group/fetchAllGroups/" + instancia + "?getParticipants=false"),
                "listar os grupos"));
        List<GrupoWhatsapp> out = new ArrayList<>();
        for (JsonNode g : grupos) {
            out.add(new GrupoWhatsapp(g.path("id").asText(), g.path("subject").asText()));
        }
        out.sort((a, b) -> a.nome().compareToIgnoreCase(b.nome()));
        return out;
    }

    @Override
    public Map<String, Object> situacao() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "evolution");
        m.put("instancia", URLDecoderSafe.decode(instancia));
        try {
            Resposta r = get("/instance/connectionState/" + instancia);
            if (r.status() == 404) {
                m.put("connected", false);
                m.put("state", "sem_instancia");
                return m;
            }
            String estado = json(exigir(r, "ler a situação")).path("instance").path("state").asText("");
            m.put("state", estado);
            m.put("connected", "open".equals(estado));
        } catch (RuntimeException e) {
            m.put("erro", e.getMessage());
        }
        return m;
    }

    @Override
    public Map<String, Object> conectar() {
        Map<String, Object> m = new LinkedHashMap<>();
        Resposta estado = get("/instance/connectionState/" + instancia);
        JsonNode qr;
        if (estado.status() == 404) {
            // primeira vez: cria a instância já pedindo o QR Code
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("instanceName", URLDecoderSafe.decode(instancia));
            corpo.put("integration", "WHATSAPP-BAILEYS");
            corpo.put("qrcode", true);
            corpo.put("groupsIgnore", true);   // não precisamos receber as mensagens dos grupos
            corpo.put("readMessages", false);
            corpo.put("alwaysOnline", false);
            corpo.put("syncFullHistory", false);
            corpo.put("rejectCall", false);
            Resposta criada = exigir(chamarSemFalha("POST", "/instance/create", corpo), "criar a instância");
            qr = json(criada).path("qrcode");
        } else {
            if ("open".equals(json(exigir(estado, "ler a situação")).path("instance").path("state").asText())) {
                m.put("conectado", true);
                return m;
            }
            qr = json(exigir(get("/instance/connect/" + instancia), "gerar o QR Code"));
        }
        if ("open".equals(qr.path("instance").path("state").asText())) {
            m.put("conectado", true);
            return m;
        }
        m.put("conectado", false);
        String base64 = qr.path("base64").asText("");
        if (!base64.isBlank()) {
            m.put("qrCode", base64.startsWith("data:") ? base64 : "data:image/png;base64," + base64);
        }
        if (!qr.path("pairingCode").asText("").isBlank()) {
            m.put("codigoPareamento", qr.path("pairingCode").asText());
        }
        if (!m.containsKey("qrCode")) {
            m.put("mensagem", "A Evolution ainda está gerando o QR Code. Tente de novo em alguns segundos.");
        }
        return m;
    }

    // ------------------------------------------------------------------ HTTP

    private Resposta get(String caminho) {
        try {
            return chamar("GET", caminho, null);
        } catch (IOException e) {
            throw new FalhaEnvio("Falha de rede com a Evolution (" + base + "): " + e.getMessage(), e);
        }
    }

    private Resposta chamarSemFalha(String metodo, String caminho, Object corpo) {
        try {
            return chamar(metodo, caminho, mapper.writeValueAsString(corpo));
        } catch (IOException e) {
            throw new FalhaEnvio("Falha de rede com a Evolution (" + base + "): " + e.getMessage(), e);
        }
    }

    private Resposta chamar(String metodo, String caminho, String corpo) throws IOException {
        HttpRequest.Builder b;
        try {
            b = HttpRequest.newBuilder(URI.create(base + caminho));
        } catch (IllegalArgumentException e) {
            throw new FalhaEnvio("Endereço da Evolution inválido: confira EVOLUTION_URL no Railway (" + e.getMessage() + ")", e);
        }
        b.timeout(Duration.ofSeconds(60)) // listar grupos busca a foto de cada um e pode demorar
                .header("apikey", apiKey)
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resposta(r.statusCode(), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaEnvio("Interrompido", e);
        }
    }

    private Resposta exigir(Resposta r, String acao) {
        if (r.status() == 401 || r.status() == 403) {
            throw new FalhaEnvio("A Evolution recusou a chave: confira se EVOLUTION_API_KEY no backend é igual a "
                    + "AUTHENTICATION_API_KEY do serviço da Evolution.");
        }
        if (r.status() == 404) {
            throw new FalhaEnvio("A instância \"" + URLDecoderSafe.decode(instancia)
                    + "\" não existe na Evolution: clique em Conectar número para criá-la.");
        }
        if (!r.ok()) {
            throw new FalhaEnvio("Evolution respondeu HTTP " + r.status() + " ao " + acao + ": " + corta(r.corpo()));
        }
        return r;
    }

    private JsonNode json(Resposta r) {
        try {
            return r.corpo() == null || r.corpo().isBlank() ? mapper.createObjectNode() : mapper.readTree(r.corpo());
        } catch (IOException e) {
            throw new FalhaEnvio("Resposta inesperada da Evolution: " + corta(r.corpo()), e);
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String corta(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) : s;
    }

    private static final class URLDecoderSafe {
        static String decode(String s) {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        }
    }
}
