package com.precificacao.precificacao.food99;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Chamadas à 99Food Open Platform que usamos (só leitura):
 * - token do financeiro (/v3/auth/authtoken/signIn, app_id + app_secret; vale ~6h);
 * - lojas vinculadas ao app (/v1/shop/shop/list, com assinatura MD5);
 * - link de autorização para o restaurante vincular a loja (/v1/auth/authorizationpage/getUrl);
 * - lançamentos do financeiro por dia (/v3/finance/finance/getShopBillDetail, paginado, até 200 por página).
 * Toda resposta vem como {errno, errmsg, data}; errno diferente de 0 vira Food99Exception.
 */
@Component
public class Food99Client {

    static final String TOKEN = "/v3/auth/authtoken/signIn";
    static final String LOJAS = "/v1/shop/shop/list";
    static final String LINK_AUTORIZACAO = "/v1/auth/authorizationpage/getUrl";
    static final String EXTRATO = "/v3/finance/finance/getShopBillDetail";
    static final DateTimeFormatter DATA = DateTimeFormatter.BASIC_ISO_DATE;

    public record Loja(String shopId, String appShopId, String nome, boolean vinculada) {
    }

    public record Pagina(List<JsonNode> itens, int totalPaginas) {
    }

    private final Food99Properties props;
    private final Food99Api api;
    private final Clock clock;
    private String token;
    private Instant renovarEm = Instant.EPOCH;

    public Food99Client(Food99Properties props, Food99Api api, Clock clock) {
        this.props = props;
        this.api = api;
        this.clock = clock;
    }

    public boolean configurado() {
        return props.configurado();
    }

    // ------------------------------------------------------------------ chamadas

    public List<Loja> lojas() {
        List<Loja> out = new ArrayList<>();
        int pagina = 1;
        int total;
        do {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("app_id", appId());
            p.put("page_no", pagina);
            p.put("page_size", 100);
            p.put("timestamp", clock.instant().getEpochSecond());
            p.put("sign", assinar(p, props.appSecret()));
            JsonNode d = dados(api.post(LOJAS, p, null), LOJAS);
            for (JsonNode s : d.path("shops")) {
                String app = s.path("app_shop_id").asText("");
                out.add(new Loja(s.path("shop_id").asText(), app.isBlank() ? null : app, s.path("shop_name").asText(null),
                        s.path("bound_flag").asInt(0) == 1));
            }
            total = d.path("total_page").asInt(1);
            pagina++;
        } while (pagina <= total);
        return out;
    }

    public String linkAutorizacao() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("app_id", appId());
        return dados(api.post(LINK_AUTORIZACAO, p, null), LINK_AUTORIZACAO).path("url").asText();
    }

    /** Uma página dos lançamentos do financeiro de uma loja (acceptor_code = app_shop_id) no período. */
    public Pagina extrato(String acceptorCode, LocalDate de, LocalDate ate, int pagina) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("acceptor_code", acceptorCode);
        p.put("page_no", pagina);
        p.put("page_size", 200);
        p.put("start_date", de.format(DATA));
        p.put("end_date", ate.format(DATA));
        JsonNode resp;
        try {
            resp = api.post(EXTRATO, p, token(false));
            verificar(resp, EXTRATO);
        } catch (Food99Exception e) {
            // token vencido/recusado: refaz uma vez
            resp = api.post(EXTRATO, p, token(true));
        }
        JsonNode d = dados(resp, EXTRATO);
        List<JsonNode> itens = new ArrayList<>();
        d.path("data").forEach(itens::add);
        return new Pagina(itens, d.path("total_page").asInt(1));
    }

    // ------------------------------------------------------------------ token

    synchronized String token(boolean forcar) {
        if (forcar || token == null || !clock.instant().isBefore(renovarEm)) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("retailer", props.appId().strip());
            p.put("secret", props.appSecret().strip());
            JsonNode r = api.post(TOKEN, p, null);
            JsonNode d = r.has("data") && r.path("data").has("accessToken") ? r.path("data") : r;
            if (r.has("errno") && r.path("errno").asInt() != 0 || !d.hasNonNull("accessToken")) {
                throw new Food99Exception(r.path("errno").asInt(-1), "Token do financeiro da 99Food recusado: "
                        + r.path("errmsg").asText("sem accessToken na resposta"));
            }
            token = d.path("accessToken").asText();
            long validade = d.path("expiresIn").asLong(3600);
            renovarEm = clock.instant().plusSeconds(Math.max(60, validade - 600));
        }
        return token;
    }

    // ------------------------------------------------------------------ apoio

    private Object appId() {
        if (!configurado()) {
            throw new Food99Exception(0, "Credenciais da 99Food não configuradas (FOOD99_APP_ID e FOOD99_APP_SECRET)");
        }
        String id = props.appId().strip();
        return id.matches("\\d+") ? new BigInteger(id) : id;
    }

    static void verificar(JsonNode r, String caminho) {
        if (r.has("errno") && r.path("errno").asInt() != 0) {
            throw new Food99Exception(r.path("errno").asInt(), "99Food recusou " + caminho + ": errno "
                    + r.path("errno").asInt() + " — " + r.path("errmsg").asText(""));
        }
    }

    static JsonNode dados(JsonNode r, String caminho) {
        verificar(r, caminho);
        return r.has("data") ? r.path("data") : r;
    }

    /**
     * Assinatura da 99Food: parâmetros não vazios (sem o próprio sign) em ordem alfabética, "k=v" unidos por "&",
     * com o app_secret no fim; MD5 em hexadecimal minúsculo.
     */
    static String assinar(Map<String, Object> params, String secret) {
        String base = new TreeMap<>(params).entrySet().stream()
                .filter(e -> !e.getKey().equals("sign") && e.getValue() != null && !String.valueOf(e.getValue()).isEmpty())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + secret;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(base.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
