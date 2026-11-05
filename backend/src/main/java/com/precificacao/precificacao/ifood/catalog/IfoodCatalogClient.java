package com.precificacao.precificacao.ifood.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.IfoodHttp;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Módulo Catalog v2 do iFood: catálogos, categorias, itens, complementos e fotos.
 *
 * Comportamentos da API real que este cliente já trata (vistos na homologação):
 * - GET /catalog/version responde texto puro ("v2"), não JSON;
 * - a listagem de categorias com include_items=true pode vir com "items": [];
 *   os itens estão em GET /categories/{id}/items;
 * - nessa resposta o NOME do item fica no "product" (item.productId), não no item;
 * - mandar só "value" no preço apaga o "originalValue"; mandamos os dois.
 */
@Component
public class IfoodCatalogClient {

    static final String BASE = "/catalog/v2.0";

    private final IfoodHttp http;

    public IfoodCatalogClient(IfoodHttp http) {
        this.http = http;
    }

    private static String loja(String merchantId) {
        return BASE + "/merchants/" + merchantId;
    }

    // ------------------------------------------------------------- leitura

    /** "v2" ou "v1". */
    public String versao(String merchantId) {
        JsonNode r = http.get(loja(merchantId) + "/catalog/version");
        return r.isTextual() ? r.asText() : r.path("version").asText(r.toString());
    }

    public List<JsonNode> catalogos(String merchantId) {
        return lista(http.get(loja(merchantId) + "/catalogs"));
    }

    public List<JsonNode> categorias(String merchantId, String catalogId, boolean incluirItens) {
        return lista(http.get(loja(merchantId) + "/catalogs/" + catalogId + "/categories",
                Map.of("include_items", String.valueOf(incluirItens))));
    }

    /** Resposta crua de GET /categories/{id}/items: items, products, optionGroups e options. */
    public JsonNode itensDaCategoriaBruto(String merchantId, String categoryId) {
        return http.get(loja(merchantId) + "/categories/" + categoryId + "/items");
    }

    /** Itens da categoria já com nome, descrição e foto vindos do "product". */
    public List<ObjectNode> itensDaCategoria(String merchantId, String categoryId) {
        return normalizarItens(itensDaCategoriaBruto(merchantId, categoryId));
    }

    /**
     * Itens de uma categoria vinda de categorias(..., true): usa os itens embutidos e,
     * se vierem vazios, busca em /categories/{id}/items.
     */
    public List<ObjectNode> itensDe(String merchantId, JsonNode categoria) {
        JsonNode embutidos = categoria.path("items");
        if (embutidos.isArray() && !embutidos.isEmpty()) {
            List<ObjectNode> out = new ArrayList<>();
            embutidos.forEach(i -> { if (i.isObject()) out.add((ObjectNode) i); });
            return out;
        }
        return itensDaCategoria(merchantId, categoria.path("id").asText());
    }

    public JsonNode naoVendaveis(String merchantId, String catalogId) {
        return http.get(loja(merchantId) + "/catalogs/" + catalogId + "/unsellableItems");
    }

    public JsonNode itemCompleto(String merchantId, String itemId) {
        return http.get(loja(merchantId) + "/items/" + itemId + "/flat");
    }

    public JsonNode statusLote(String merchantId, String batchId) {
        return http.get(loja(merchantId) + "/batch/" + batchId);
    }

    /** Junta em cada item os campos do seu product (nome, descrição, foto, código). */
    public static List<ObjectNode> normalizarItens(JsonNode resposta) {
        List<ObjectNode> out = new ArrayList<>();
        if (resposta == null || resposta.isNull()) {
            return out;
        }
        if (resposta.isArray()) {
            resposta.forEach(i -> { if (i.isObject()) out.add((ObjectNode) i); });
            return out;
        }
        Map<String, JsonNode> produtos = new HashMap<>();
        for (JsonNode p : resposta.path("products")) {
            produtos.put(p.path("id").asText(), p);
        }
        for (JsonNode i : resposta.path("items")) {
            if (!i.isObject()) {
                continue;
            }
            ObjectNode item = ((ObjectNode) i).deepCopy();
            JsonNode prod = produtos.get(item.path("productId").asText());
            if (prod != null) {
                for (String campo : new String[]{"name", "description", "imagePath", "ean"}) {
                    String atual = item.path(campo).asText("");
                    String doProduto = prod.path(campo).asText("");
                    if (atual.isBlank() && !doProduto.isBlank()) {
                        item.put(campo, doProduto);
                    }
                }
            }
            out.add(item);
        }
        return out;
    }

    // ------------------------------------------------------------- escrita

    public JsonNode criarCategoria(String merchantId, String catalogId, String nome) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", nome);
        corpo.put("status", "AVAILABLE");
        corpo.put("template", "DEFAULT");
        corpo.put("sequence", 0);
        return http.post(loja(merchantId) + "/catalogs/" + catalogId + "/categories", corpo);
    }

    /** PUT /items: cria ou substitui o item inteiro (item, products, optionGroups, options). */
    public JsonNode salvarItem(String merchantId, JsonNode itemCompleto) {
        return http.put(loja(merchantId) + "/items", itemCompleto);
    }

    /** status: AVAILABLE ou UNAVAILABLE. */
    public JsonNode alterarStatusItem(String merchantId, String itemId, String status) {
        return http.patch(loja(merchantId) + "/items/status", Map.of("itemId", itemId, "status", status));
    }

    public JsonNode alterarPrecoItem(String merchantId, String itemId, BigDecimal valor, BigDecimal valorOriginal) {
        return http.patch(loja(merchantId) + "/items/price", Map.of("itemId", itemId, "price", preco(valor, valorOriginal)));
    }

    public JsonNode alterarStatusComplemento(String merchantId, String optionId, String status) {
        return http.patch(loja(merchantId) + "/options/status", Map.of("optionId", optionId, "status", status));
    }

    public JsonNode alterarPrecoComplemento(String merchantId, String optionId, BigDecimal valor, BigDecimal valorOriginal) {
        return http.patch(loja(merchantId) + "/options/price", Map.of("optionId", optionId, "price", preco(valor, valorOriginal)));
    }

    /**
     * Envia uma foto (JPG ou PNG) e devolve o caminho para usar em product.imagePath.
     * Tenta primeiro como data URL e, se o iFood recusar o formato, como base64 puro.
     */
    public String enviarFoto(String merchantId, byte[] conteudo, String tipo) {
        if (!"image/jpeg".equals(tipo) && !"image/png".equals(tipo)) {
            throw new IllegalArgumentException("Use foto JPG ou PNG (recebido: " + tipo + ")");
        }
        String b64 = Base64.getEncoder().encodeToString(conteudo);
        String caminho = loja(merchantId) + "/image/upload";
        JsonNode r;
        try {
            r = http.post(caminho, Map.of("image", "data:" + tipo + ";base64," + b64));
        } catch (IfoodException primeira) {
            if (primeira.getStatus() != 400 && primeira.getStatus() != 415 && primeira.getStatus() != 422) {
                throw primeira;
            }
            r = http.post(caminho, Map.of("image", b64));
        }
        String path = null;
        if (r.isObject()) {
            for (String campo : new String[]{"path", "imagePath", "url", "image"}) {
                if (r.hasNonNull(campo)) {
                    path = r.get(campo).asText();
                    break;
                }
            }
        } else if (r.isTextual()) {
            path = r.asText().replace("\"", "").trim();
        }
        if (path == null || path.isBlank()) {
            throw new IfoodException(200, "o iFood não devolveu o caminho da imagem", r.toString());
        }
        return path;
    }

    // ------------------------------------------------------------- apoio

    private static Map<String, BigDecimal> preco(BigDecimal valor, BigDecimal original) {
        Map<String, BigDecimal> p = new LinkedHashMap<>();
        p.put("value", valor);
        p.put("originalValue", original != null ? original : valor);
        return p;
    }

    private static List<JsonNode> lista(JsonNode r) {
        List<JsonNode> out = new ArrayList<>();
        if (r instanceof ArrayNode a) {
            a.forEach(out::add);
        }
        return out;
    }
}
