package com.precificacao.precificacao.cardapio;

import com.fasterxml.jackson.databind.JsonNode;
import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Catalogo;
import com.precificacao.precificacao.cardapio.CardapioModelos.Categoria;
import com.precificacao.precificacao.cardapio.CardapioModelos.Grupo;
import com.precificacao.precificacao.cardapio.CardapioModelos.Item;
import com.precificacao.precificacao.cardapio.CardapioModelos.Opcao;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.catalog.IfoodCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê o cardápio completo de uma loja no Catalog v2:
 * catálogos → categorias → GET /categories/{id}/items (itens, produtos, grupos de complemento e complementos).
 * Preço e status de cada item/complemento já vêm ajustados pelo contexto do catálogo (contextModifiers).
 */
@Component
public class LeitorCardapio {

    private static final Logger log = LoggerFactory.getLogger(LeitorCardapio.class);
    static final String URL_FOTOS = "https://static-images.ifood.com.br/image/upload/t_medium/pratos/";

    /** Leitura do iFood, separada para os testes poderem trocar por respostas prontas. */
    public interface Fonte {
        List<JsonNode> catalogos(String merchantId);

        List<JsonNode> categorias(String merchantId, String catalogId);

        JsonNode itensDaCategoria(String merchantId, String categoryId);

        JsonNode naoVendaveis(String merchantId, String catalogId);
    }

    private final Fonte fonte;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public LeitorCardapio(IfoodCatalogClient catalog, Clock clock) {
        this(new Fonte() {
            @Override
            public List<JsonNode> catalogos(String merchantId) {
                return catalog.catalogos(merchantId);
            }

            @Override
            public List<JsonNode> categorias(String merchantId, String catalogId) {
                return catalog.categorias(merchantId, catalogId, false);
            }

            @Override
            public JsonNode itensDaCategoria(String merchantId, String categoryId) {
                return catalog.itensDaCategoriaBruto(merchantId, categoryId);
            }

            @Override
            public JsonNode naoVendaveis(String merchantId, String catalogId) {
                return catalog.naoVendaveis(merchantId, catalogId);
            }
        }, clock);
    }

    public LeitorCardapio(Fonte fonte, Clock clock) {
        this.fonte = fonte;
        this.clock = clock;
    }

    public Cardapio ler(String merchantId) {
        List<Catalogo> catalogos = new ArrayList<>();
        for (JsonNode cat : fonte.catalogos(merchantId)) {
            String catalogId = texto(cat, "catalogId", texto(cat, "id", null));
            if (catalogId == null) {
                continue;
            }
            JsonNode ctx = cat.path("context");
            String contexto = ctx.isArray() && !ctx.isEmpty() ? ctx.get(0).asText() : ctx.isTextual() ? ctx.asText() : "DEFAULT";
            catalogos.add(lerCatalogo(merchantId, catalogId, contexto, texto(cat, "status", null)));
        }
        // Delivery (DEFAULT) primeiro, depois os outros canais
        catalogos.sort(Comparator.comparing((Catalogo c) -> !"DEFAULT".equalsIgnoreCase(c.contexto())));
        return new Cardapio(merchantId, OffsetDateTime.now(clock), catalogos);
    }

    private Catalogo lerCatalogo(String merchantId, String catalogId, String contexto, String status) {
        Map<String, String> naoVendaveis = new HashMap<>();
        try {
            coletarRestricoes(fonte.naoVendaveis(merchantId, catalogId), naoVendaveis);
        } catch (IfoodException e) {
            log.debug("unsellableItems falhou em {}: {}", merchantId, e.getMessage());
        }
        List<Categoria> categorias = new ArrayList<>();
        for (JsonNode k : fonte.categorias(merchantId, catalogId)) {
            String id = texto(k, "id", null);
            if (id == null) {
                continue;
            }
            List<Item> itens = lerItens(fonte.itensDaCategoria(merchantId, id), contexto, naoVendaveis);
            categorias.add(new Categoria(id, texto(k, "name", "(sem nome)"), texto(k, "status", null),
                    k.path("index").asInt(k.path("sequence").asInt(0)), itens));
        }
        categorias.sort(Comparator.comparingInt(Categoria::ordem));
        return new Catalogo(catalogId, contexto, status, categorias);
    }

    /** Monta os itens a partir da resposta de GET /categories/{id}/items. */
    static List<Item> lerItens(JsonNode resp, String contexto, Map<String, String> naoVendaveis) {
        List<Item> out = new ArrayList<>();
        if (resp == null || !resp.isObject()) {
            return out;
        }
        Map<String, JsonNode> produtos = porId(resp.path("products"));
        Map<String, JsonNode> grupos = porId(resp.path("optionGroups"));
        Map<String, JsonNode> opcoes = porId(resp.path("options"));

        for (JsonNode i : resp.path("items")) {
            String id = texto(i, "id", null);
            if (id == null) {
                continue;
            }
            JsonNode prod = produtos.getOrDefault(texto(i, "productId", ""), i);
            JsonNode efetivo = noContexto(i, contexto);
            String naoVende = naoVendaveis.getOrDefault(id, naoVendaveis.get(texto(i, "productId", "")));

            List<Grupo> gs = new ArrayList<>();
            for (JsonNode ref : prod.path("optionGroups")) {
                String gid = ref.isTextual() ? ref.asText() : texto(ref, "id", null);
                JsonNode g = gid == null ? null : grupos.get(gid);
                if (g == null) {
                    continue;
                }
                List<Opcao> ops = new ArrayList<>();
                List<JsonNode> idsOpcoes = new ArrayList<>();
                g.path("optionIds").forEach(idsOpcoes::add);
                if (idsOpcoes.isEmpty()) {
                    g.path("options").forEach(idsOpcoes::add);
                }
                for (JsonNode oref : idsOpcoes) {
                    String oid = oref.isTextual() ? oref.asText() : texto(oref, "id", null);
                    JsonNode o = oid == null ? null : opcoes.getOrDefault(oid, oref.isObject() ? oref : null);
                    if (o == null) {
                        continue;
                    }
                    JsonNode oprod = produtos.getOrDefault(texto(o, "productId", ""), o);
                    JsonNode oef = noContexto(o, contexto);
                    String ofoto = texto(oprod, "imagePath", null);
                    ops.add(new Opcao(oid, texto(o, "productId", null), texto(oprod, "name", texto(o, "name", oid)),
                            texto(oef, "status", null), valor(oef.path("price"), "value"),
                            valor(oef.path("price"), "originalValue"), texto(o, "externalCode", null),
                            ofoto == null ? null : ofoto.startsWith("http") ? ofoto : URL_FOTOS + ofoto));
                }
                gs.add(new Grupo(gid, texto(g, "name", "Complementos"), texto(g, "status", null),
                        inteiro(ref.isObject() ? ref.path("min") : g.path("min")),
                        inteiro(ref.isObject() ? ref.path("max") : g.path("max")), ops));
            }

            String foto = texto(prod, "imagePath", texto(i, "imagePath", null));
            out.add(new Item(id, texto(i, "productId", null), texto(prod, "name", texto(i, "name", "(sem nome)")),
                    texto(prod, "description", texto(i, "description", null)),
                    foto == null ? null : foto.startsWith("http") ? foto : URL_FOTOS + foto,
                    texto(i, "externalCode", texto(prod, "externalCode", null)), texto(efetivo, "status", null),
                    valor(efetivo.path("price"), "value"), valor(efetivo.path("price"), "originalValue"),
                    naoVende, i.path("index").asInt(0), gs));
        }
        out.sort(Comparator.comparingInt(Item::ordem));
        return out;
    }

    /** status/price do item com os contextModifiers do canal do catálogo aplicados. */
    static JsonNode noContexto(JsonNode entidade, String contexto) {
        com.fasterxml.jackson.databind.node.ObjectNode out = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        out.set("status", entidade.path("status"));
        out.set("price", entidade.path("price"));
        for (JsonNode m : entidade.path("contextModifiers")) {
            if (contexto != null && contexto.equalsIgnoreCase(m.path("catalogContext").asText())) {
                if (m.hasNonNull("status")) {
                    out.set("status", m.get("status"));
                }
                if (m.hasNonNull("price")) {
                    out.set("price", m.get("price"));
                }
            }
        }
        return out;
    }

    /** /unsellableItems devolve itens com "restrictions" em algum nível; guarda id → restrições. */
    static void coletarRestricoes(JsonNode no, Map<String, String> out) {
        if (no == null) {
            return;
        }
        if (no.isArray()) {
            no.forEach(x -> coletarRestricoes(x, out));
            return;
        }
        if (!no.isObject()) {
            return;
        }
        if (no.has("restrictions")) {
            List<String> rs = new ArrayList<>();
            no.path("restrictions").forEach(r -> rs.add(r.isTextual() ? r.asText() : r.toString()));
            String txt = rs.isEmpty() ? "sim" : String.join(", ", rs);
            for (String k : new String[]{"id", "itemId", "productId"}) {
                if (no.hasNonNull(k)) {
                    out.put(no.get(k).asText(), txt);
                }
            }
        }
        no.elements().forEachRemaining(v -> {
            if (v.isContainerNode()) {
                coletarRestricoes(v, out);
            }
        });
    }

    private static Map<String, JsonNode> porId(JsonNode lista) {
        Map<String, JsonNode> m = new HashMap<>();
        for (JsonNode n : lista) {
            if (n.hasNonNull("id")) {
                m.put(n.get("id").asText(), n);
            }
        }
        return m;
    }

    private static String texto(JsonNode n, String campo, String padrao) {
        JsonNode v = n == null ? null : n.get(campo);
        return v == null || v.isNull() || v.asText().isBlank() ? padrao : v.asText();
    }

    private static BigDecimal valor(JsonNode preco, String campo) {
        JsonNode v = preco.isNumber() && "value".equals(campo) ? preco : preco.path(campo);
        if (v.isNumber()) {
            return v.decimalValue().setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (v.isTextual() && !v.asText().isBlank()) {
            try {
                return new BigDecimal(v.asText()).setScale(2, java.math.RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Integer inteiro(JsonNode n) {
        return n != null && n.isNumber() ? n.asInt() : null;
    }
}
