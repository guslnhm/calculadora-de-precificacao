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
    private final String principal;
    /** Instâncias dos números extras (cadastrados no banco), para achar um número conectado. */
    private java.util.function.Supplier<List<String>> numerosExtras = List::of;
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
        this.principal = props.evolutionInstancia();
        this.mapper = mapper;
    }

    public void usarNumerosExtras(java.util.function.Supplier<List<String>> extras) {
        this.numerosExtras = extras;
    }

    @Override
    public boolean variosNumeros() {
        return true;
    }

    @Override
    public String instanciaPrincipal() {
        return principal;
    }

    /** Nome da instância (null/vazio = a principal), já codificado para ir no caminho da URL. */
    private String inst(String instancia) {
        String nome = instancia == null || instancia.isBlank() ? principal : instancia.strip();
        return URLEncoder.encode(nome, StandardCharsets.UTF_8).replace("+", "%20");
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
        enviar(null, chatId, texto);
    }

    @Override
    public void enviar(String instancia, String chatId, String texto) {
        String instanciaUrl = inst(instancia);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("number", jid(chatId));
        corpo.put("text", texto);
        String erro = null;
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            try {
                Resposta r = chamar("POST", "/message/sendText/" + instanciaUrl, mapper.writeValueAsString(corpo));
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
        return listarGrupos(null);
    }

    @Override
    public List<GrupoWhatsapp> listarGrupos(String instancia) {
        String instanciaUrl = inst(instancia);
        JsonNode grupos = json(exigir(get("/group/fetchAllGroups/" + instanciaUrl + "?getParticipants=false"),
                "listar os grupos", instanciaUrl));
        List<GrupoWhatsapp> out = new ArrayList<>();
        for (JsonNode g : grupos) {
            out.add(new GrupoWhatsapp(g.path("id").asText(), g.path("subject").asText()));
        }
        out.sort((a, b) -> a.nome().compareToIgnoreCase(b.nome()));
        return out;
    }

    @Override
    public Map<String, Object> situacao() {
        return situacao(null);
    }

    @Override
    public Map<String, Object> situacao(String instancia) {
        String instanciaUrl = inst(instancia);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("provider", "evolution");
        m.put("instancia", URLDecoderSafe.decode(instanciaUrl));
        try {
            Resposta r = get("/instance/connectionState/" + instanciaUrl);
            if (r.status() == 404) {
                m.put("connected", false);
                m.put("state", "sem_instancia");
                return m;
            }
            String estado = json(exigir(r, "ler a situação", instanciaUrl)).path("instance").path("state").asText("");
            m.put("state", estado);
            m.put("connected", "open".equals(estado));
        } catch (RuntimeException e) {
            m.put("erro", e.getMessage());
        }
        return m;
    }

    @Override
    public Map<String, Object> conectar() {
        return conectar(null);
    }

    @Override
    public Map<String, Object> conectar(String instancia) {
        return conectar(instancia, null);
    }

    @Override
    public Map<String, Object> conectarPorCodigo(String instancia, String telefone) {
        String tel = telefone(telefone);
        if (tel == null) {
            throw new FalhaEnvio("Informe o número do WhatsApp com DDD, por exemplo 61 99999-8888");
        }
        tel = numeroNoWhatsapp(tel, instancia);
        Map<String, Object> m = conectar(instancia, tel);
        if (Boolean.TRUE.equals(m.get("conectado")) || m.containsKey("codigoPareamento")) {
            m.put("numeroUsado", tel);
            return m;
        }
        // A Evolution só gera o código quando a tentativa de conexão é feita já com o número. Se a instância
        // acabou de ser criada (ou estava no meio de uma tentativa por QR Code), o código não vem de primeira:
        // pede de novo e, se preciso, reinicia a tentativa (logout) e pede outra vez.
        String instanciaUrl = inst(instancia);
        String ultima = "";
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            if (tentativa == 1) {
                chamarSemFalha("DELETE", "/instance/logout/" + instanciaUrl, Map.of());
            }
            dormir(tentativa == 0 ? 2500 : 3500);
            Resposta r = get("/instance/connect/" + instanciaUrl + "?number=" + tel);
            ultima = r.status() + " " + resumo(r.corpo());
            if (!r.ok()) {
                continue;
            }
            JsonNode j = json(r);
            if ("open".equals(j.path("instance").path("state").asText())) {
                return Map.of("conectado", true);
            }
            String codigo = j.path("pairingCode").asText(j.path("qrcode").path("pairingCode").asText(""));
            if (!codigo.isBlank() && !"null".equals(codigo)) {
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("conectado", false);
                ok.put("codigoPareamento", codigo);
                ok.put("numeroUsado", tel);
                return ok;
            }
        }
        m.remove("qrCode");
        m.put("mensagem", "A Evolution não devolveu o código depois de 3 tentativas. Resposta dela: " + ultima);
        return m;
    }

    /**
     * O código de pareamento só funciona com o número exatamente como o WhatsApp o registrou. No Brasil, muitas
     * contas (DDD 31 em diante, como 61) estão registradas sem o 9 extra. Pergunta ao WhatsApp, por um número
     * já conectado, qual é o número real da conta; se não der para perguntar, tira o 9 nesses DDDs.
     */
    String numeroNoWhatsapp(String tel, String instancia) {
        List<String> candidatos = new ArrayList<>();
        candidatos.add(principal);
        for (String n : numerosExtras.get()) {
            if (!n.equals(instancia)) {
                candidatos.add(n);
            }
        }
        for (String inst : candidatos) {
            if (inst == null || inst.equals(instancia)) {
                continue;
            }
            try {
                Resposta r = chamarSemFalha("POST", "/chat/whatsappNumbers/" + inst(inst), Map.of("numbers", List.of(tel)));
                if (!r.ok()) {
                    continue;
                }
                for (JsonNode n : json(r)) {
                    if (n.path("exists").asBoolean(false)) {
                        String jid = n.path("jid").asText("");
                        String digitos = jid.contains("@") ? jid.substring(0, jid.indexOf('@')).replaceAll("\\D", "") : "";
                        if (digitos.length() >= 12) {
                            return digitos;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // tenta o próximo número conectado
            }
        }
        return semNonoDigito(tel);
    }

    /** 55 + DDD ≥ 31 + 9 + 8 dígitos → sem o 9 (como o WhatsApp registra essas contas). */
    static String semNonoDigito(String tel) {
        if (tel.length() == 13 && tel.startsWith("55") && tel.charAt(4) == '9') {
            int ddd = Integer.parseInt(tel.substring(2, 4));
            if (ddd >= 31) {
                return tel.substring(0, 4) + tel.substring(5);
            }
        }
        return tel;
    }

    /** Resposta da Evolution sem as partes longas/sensíveis (QR em base64 e o "code" da sessão). */
    static String resumo(String corpo) {
        if (corpo == null) {
            return "";
        }
        String t = corpo.replaceAll("\"(base64|code)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"…\"");
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }

    /** "(61) 99999-8888" → "5561999998888"; null se não parece um celular. */
    static String telefone(String bruto) {
        if (bruto == null) {
            return null;
        }
        String d = bruto.replaceAll("\\D", "");
        if (d.length() == 10 || d.length() == 11) {
            d = "55" + d;
        }
        return d.length() >= 12 && d.length() <= 13 ? d : null;
    }

    private Map<String, Object> conectar(String instancia, String telefone) {
        String instanciaUrl = inst(instancia);
        Map<String, Object> m = new LinkedHashMap<>();
        Resposta estado = get("/instance/connectionState/" + instanciaUrl);
        JsonNode qr;
        if (estado.status() == 404) {
            // primeira vez: cria a instância já pedindo o QR Code
            Map<String, Object> corpo = new LinkedHashMap<>();
            corpo.put("instanceName", URLDecoderSafe.decode(instanciaUrl));
            corpo.put("integration", "WHATSAPP-BAILEYS");
            corpo.put("qrcode", true);
            corpo.put("groupsIgnore", true);   // não precisamos receber as mensagens dos grupos
            corpo.put("readMessages", false);
            corpo.put("alwaysOnline", false);
            corpo.put("syncFullHistory", false);
            corpo.put("rejectCall", false);
            if (telefone != null) {
                corpo.put("number", telefone); // pede o código de pareamento junto
            }
            Resposta criada = exigir(chamarSemFalha("POST", "/instance/create", corpo), "criar a instância", instanciaUrl);
            qr = json(criada).path("qrcode");
        } else {
            if ("open".equals(json(exigir(estado, "ler a situação", instanciaUrl)).path("instance").path("state").asText())) {
                m.put("conectado", true);
                return m;
            }
            qr = json(exigir(get("/instance/connect/" + instanciaUrl + (telefone == null ? "" : "?number=" + telefone)),
                    "gerar o QR Code", instanciaUrl));
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
        if (!m.containsKey("qrCode") && !m.containsKey("codigoPareamento")) {
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

    private Resposta exigir(Resposta r, String acao, String instancia) {
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
