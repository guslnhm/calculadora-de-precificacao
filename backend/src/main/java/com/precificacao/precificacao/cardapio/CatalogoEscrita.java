package com.precificacao.precificacao.cardapio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Catalogo;
import com.precificacao.precificacao.cardapio.CardapioModelos.Categoria;
import com.precificacao.precificacao.cardapio.CardapioModelos.Item;
import com.precificacao.precificacao.cardapio.CardapioService.Recusado;
import com.precificacao.precificacao.ifood.catalog.IfoodCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Cadastro no cardápio do iFood pelo site: categoria, item com foto, grupo de complementos e edição
 * de nome/descrição/foto (PUT /items com o item inteiro). Preço e pausa continuam pelos PATCH do CardapioService.
 *
 * Para editar, o item é relido do iFood (GET /categories/{id}/items) e remontado no formato do PUT, para não
 * perder nada que já esteja lá (complementos, preços por canal etc.).
 */
@Service
public class CatalogoEscrita {

    private static final Logger log = LoggerFactory.getLogger(CatalogoEscrita.class);

    /** Chamadas ao Catalog usadas aqui (trocável nos testes). */
    public interface Api {
        List<JsonNode> catalogos(String merchantId);

        List<JsonNode> categorias(String merchantId, String catalogId);

        JsonNode criarCategoria(String merchantId, String catalogId, String nome);

        JsonNode itensDaCategoria(String merchantId, String categoryId);

        JsonNode salvarItem(String merchantId, JsonNode payload);

        JsonNode statusLote(String merchantId, String batchId);

        String enviarFoto(String merchantId, byte[] conteudo, String tipo);
    }

    public record NovoComplemento(String nome, BigDecimal preco, String imagePath) {
    }

    private final Api api;
    private final MonitorItensService monitor;
    private final CardapioService cardapio;
    private final ObjectMapper mapper;
    private final long esperaMs;

    @org.springframework.beans.factory.annotation.Autowired
    public CatalogoEscrita(IfoodCatalogClient catalog, MonitorItensService monitor, CardapioService cardapio,
                           ObjectMapper mapper) {
        this(new Api() {
            @Override
            public List<JsonNode> catalogos(String merchantId) {
                return catalog.catalogos(merchantId);
            }

            @Override
            public List<JsonNode> categorias(String merchantId, String catalogId) {
                return catalog.categorias(merchantId, catalogId, false);
            }

            @Override
            public JsonNode criarCategoria(String merchantId, String catalogId, String nome) {
                return catalog.criarCategoria(merchantId, catalogId, nome);
            }

            @Override
            public JsonNode itensDaCategoria(String merchantId, String categoryId) {
                return catalog.itensDaCategoriaBruto(merchantId, categoryId);
            }

            @Override
            public JsonNode salvarItem(String merchantId, JsonNode payload) {
                return catalog.salvarItem(merchantId, payload);
            }

            @Override
            public JsonNode statusLote(String merchantId, String batchId) {
                return catalog.statusLote(merchantId, batchId);
            }

            @Override
            public String enviarFoto(String merchantId, byte[] conteudo, String tipo) {
                return catalog.enviarFoto(merchantId, conteudo, tipo);
            }
        }, monitor, cardapio, mapper, 2000);
    }

    public CatalogoEscrita(Api api, MonitorItensService monitor, CardapioService cardapio, ObjectMapper mapper,
                           long esperaMs) {
        this.api = api;
        this.monitor = monitor;
        this.cardapio = cardapio;
        this.mapper = mapper;
        this.esperaMs = esperaMs;
    }

    // ------------------------------------------------------------------ foto

    public String enviarFoto(String merchantId, byte[] conteudo, String tipo) {
        if (conteudo == null || conteudo.length == 0) {
            throw new Recusado("Escolha uma foto");
        }
        if (conteudo.length > 5 * 1024 * 1024) {
            throw new Recusado("A foto passa de 5 MB");
        }
        String t = tipo == null ? "" : tipo.toLowerCase();
        if (t.equals("image/jpg")) {
            t = "image/jpeg";
        }
        if (!t.equals("image/jpeg") && !t.equals("image/png")) {
            throw new Recusado("Use foto JPG ou PNG");
        }
        return api.enviarFoto(merchantId, conteudo, t);
    }

    // ------------------------------------------------------------------ categoria

    /** POST /categories no catálogo de Delivery (usa a existente se já houver uma com o mesmo nome). */
    public Cardapio criarCategoria(String merchantId, String nome) {
        String n = obrigatorio(nome, "Informe o nome da categoria");
        String catalogId = catalogoPrincipal(merchantId);
        boolean existe = api.categorias(merchantId, catalogId).stream()
                .anyMatch(c -> n.equalsIgnoreCase(c.path("name").asText().strip()));
        if (!existe) {
            api.criarCategoria(merchantId, catalogId, n);
        }
        return reler(merchantId, c -> categoriaPorNome(c, n) != null);
    }

    // ------------------------------------------------------------------ item

    /** PUT /items com item novo, ativo, com preço e foto. */
    public Cardapio criarItem(String merchantId, String categoriaId, String nome, String descricao, BigDecimal preco,
                              String imagePath) {
        String n = obrigatorio(nome, "Informe o nome do item");
        if (categoriaId == null || categoriaId.isBlank()) {
            throw new Recusado("Escolha a categoria");
        }
        BigDecimal p = precoValido(preco, false);
        String itemId = UUID.randomUUID().toString();
        String productId = UUID.randomUUID().toString();
        String ext = "SITE-" + itemId.substring(0, 8);

        ObjectNode payload = mapper.createObjectNode();
        ObjectNode item = payload.putObject("item");
        item.put("id", itemId);
        item.put("type", "DEFAULT");
        item.put("categoryId", categoriaId);
        item.put("status", "AVAILABLE");
        preco(item.putObject("price"), p);
        item.put("externalCode", ext);
        item.put("index", 0);
        item.put("productId", productId);
        ObjectNode prod = payload.putArray("products").addObject();
        prod.put("id", productId);
        prod.put("externalCode", ext);
        prod.put("name", n);
        prod.put("description", descricao == null ? "" : descricao.strip());
        prod.put("imagePath", imagePath == null ? "" : imagePath);
        prod.put("serving", "NOT_APPLICABLE");
        prod.putArray("dietaryRestrictions");
        prod.putArray("optionGroups");
        payload.putArray("optionGroups");
        payload.putArray("options");

        enviar(merchantId, payload);
        return reler(merchantId, c -> item(c, itemId) != null);
    }

    /** Muda nome, descrição e/ou foto do item (PUT /items com o item inteiro). */
    public Cardapio editarItem(String merchantId, String itemId, String nome, String descricao, String imagePath) {
        ObjectNode payload = payloadDoItem(merchantId, itemId);
        ObjectNode prod = produto(payload, payload.path("item").path("productId").asText());
        if (nome != null && !nome.isBlank()) {
            prod.put("name", nome.strip());
        }
        if (descricao != null) {
            prod.put("description", descricao.strip());
        }
        if (imagePath != null && !imagePath.isBlank()) {
            prod.put("imagePath", imagePath);
        }
        enviar(merchantId, payload);
        String esperado = nome == null || nome.isBlank() ? null : nome.strip();
        return reler(merchantId, c -> {
            Item i = item(c, itemId);
            return i != null && (esperado == null || esperado.equals(i.nome()));
        });
    }

    /** Acrescenta um grupo de complementos (com nome, preço e foto de cada um) ao item. */
    public Cardapio criarComplementos(String merchantId, String itemId, String grupo, Integer minimo, Integer maximo,
                                      List<NovoComplemento> complementos) {
        String g = obrigatorio(grupo, "Informe o nome do grupo");
        if (complementos == null || complementos.isEmpty()) {
            throw new Recusado("Informe pelo menos um complemento");
        }
        ObjectNode payload = payloadDoItem(merchantId, itemId);
        String ext = payload.path("item").path("externalCode").asText("SITE-" + itemId.substring(0, Math.min(8, itemId.length())));
        ArrayNode products = (ArrayNode) payload.get("products");
        ArrayNode options = (ArrayNode) payload.get("options");
        ArrayNode groups = (ArrayNode) payload.get("optionGroups");
        int base = options.size();
        String groupId = UUID.randomUUID().toString();
        ArrayNode optionIds = mapper.createArrayNode();
        int i = 0;
        for (NovoComplemento c : complementos) {
            String nome = obrigatorio(c.nome(), "Informe o nome de cada complemento");
            BigDecimal p = precoValido(c.preco(), true);
            i++;
            String oid = UUID.randomUUID().toString();
            String pid = UUID.randomUUID().toString();
            String codigo = ext + "-C" + (base + i);
            ObjectNode op = products.addObject();
            op.put("id", pid);
            op.put("externalCode", codigo);
            op.put("name", nome);
            op.put("description", "");
            op.put("imagePath", c.imagePath() == null ? "" : c.imagePath());
            op.put("serving", "NOT_APPLICABLE");
            op.putArray("dietaryRestrictions");
            ObjectNode o = options.addObject();
            o.put("id", oid);
            o.put("status", "AVAILABLE");
            o.put("productId", pid);
            o.put("index", i - 1);
            preco(o.putObject("price"), p);
            o.put("externalCode", codigo);
            optionIds.add(oid);
        }
        ObjectNode grp = groups.addObject();
        grp.put("id", groupId);
        grp.put("name", g);
        grp.put("status", "AVAILABLE");
        grp.put("externalCode", ext + "-G" + groups.size());
        grp.put("optionGroupType", "DEFAULT");
        grp.set("optionIds", optionIds);
        grp.put("index", groups.size() - 1);
        int min = minimo == null ? 0 : Math.max(0, minimo);
        int max = maximo == null || maximo <= 0 ? complementos.size() : maximo;
        if (min > max) {
            throw new Recusado("O mínimo não pode ser maior que o máximo");
        }
        ObjectNode ref = ((ArrayNode) produto(payload, payload.path("item").path("productId").asText())
                .withArray("optionGroups")).addObject();
        ref.put("id", groupId);
        ref.put("min", min);
        ref.put("max", max);

        enviar(merchantId, payload);
        return reler(merchantId, c -> {
            Item it = item(c, itemId);
            return it != null && it.grupos().stream().anyMatch(x -> x.id().equals(groupId));
        });
    }

    /** Muda nome e/ou foto de um complemento do item (PUT /items com o item inteiro). */
    public Cardapio editarComplemento(String merchantId, String itemId, String optionId, String nome, String imagePath) {
        ObjectNode payload = payloadDoItem(merchantId, itemId);
        JsonNode opcao = null;
        for (JsonNode o : payload.path("options")) {
            if (optionId.equals(o.path("id").asText())) {
                opcao = o;
            }
        }
        if (opcao == null) {
            throw new Recusado("Este complemento não está mais no item. Clique em Atualizar do iFood.");
        }
        ObjectNode prod = produto(payload, opcao.path("productId").asText());
        if (nome != null && !nome.isBlank()) {
            prod.put("name", nome.strip());
        }
        if (imagePath != null && !imagePath.isBlank()) {
            prod.put("imagePath", imagePath);
        }
        enviar(merchantId, payload);
        String esperado = nome == null || nome.isBlank() ? null : nome.strip();
        return reler(merchantId, c -> {
            Item it = item(c, itemId);
            return it != null && (esperado == null || it.grupos().stream()
                    .flatMap(gr -> gr.opcoes().stream()).anyMatch(o -> o.id().equals(optionId) && esperado.equals(o.nome())));
        });
    }

    // ------------------------------------------------------------------ montar o PUT a partir do GET

    private ObjectNode payloadDoItem(String merchantId, String itemId) {
        Cardapio c = cardapio.cardapio(merchantId, false);
        String categoriaId = null;
        for (Catalogo cat : c.catalogos()) {
            for (Categoria k : cat.categorias()) {
                if (k.itens().stream().anyMatch(i -> i.id().equals(itemId))) {
                    categoriaId = k.id();
                }
            }
        }
        if (categoriaId == null) {
            throw new Recusado("Não achei este item no cardápio. Clique em Atualizar do iFood e tente de novo.");
        }
        return montarPayload(api.itensDaCategoria(merchantId, categoriaId), itemId, categoriaId, mapper);
    }

    /**
     * Remonta o item no formato do PUT /items a partir do GET /categories/{id}/items: o item, o produto dele,
     * os grupos de complemento do produto, os complementos desses grupos e os produtos dos complementos.
     */
    static ObjectNode montarPayload(JsonNode bruto, String itemId, String categoriaId, ObjectMapper mapper) {
        Map<String, JsonNode> produtos = porId(bruto.path("products"));
        Map<String, JsonNode> grupos = porId(bruto.path("optionGroups"));
        Map<String, JsonNode> opcoes = porId(bruto.path("options"));
        JsonNode item = null;
        for (JsonNode i : bruto.path("items")) {
            if (itemId.equals(i.path("id").asText())) {
                item = i;
            }
        }
        if (item == null) {
            throw new Recusado("O iFood ainda não mostra este item na categoria. Espere alguns segundos e tente de novo.");
        }
        ObjectNode out = mapper.createObjectNode();
        ObjectNode it = copiar(out.putObject("item"), item, "id", "type", "status", "price", "externalCode", "index",
                "productId", "contextModifiers", "shifts", "tags");
        if (!it.hasNonNull("type")) {
            it.put("type", "DEFAULT");
        }
        it.put("categoryId", categoriaId);
        if (!it.hasNonNull("externalCode")) {
            it.put("externalCode", "SITE-" + itemId.substring(0, Math.min(8, itemId.length())));
        }
        ArrayNode prods = out.putArray("products");
        ArrayNode gs = out.putArray("optionGroups");
        ArrayNode os = out.putArray("options");
        Set<String> produtosIncluidos = new LinkedHashSet<>();

        JsonNode prod = produtos.getOrDefault(item.path("productId").asText(), mapper.createObjectNode());
        ObjectNode principal = produto(prod, item.path("productId").asText(), mapper);
        ArrayNode refs = principal.putArray("optionGroups");
        prods.add(principal);
        produtosIncluidos.add(principal.path("id").asText());

        for (JsonNode ref : prod.path("optionGroups")) {
            String gid = ref.isTextual() ? ref.asText() : ref.path("id").asText();
            JsonNode g = grupos.get(gid);
            if (g == null) {
                continue;
            }
            ObjectNode r = refs.addObject();
            r.put("id", gid);
            int qtd = g.path("optionIds").size();
            r.put("min", ref.has("min") ? ref.path("min").asInt() : g.path("min").asInt(0));
            r.put("max", ref.has("max") ? ref.path("max").asInt() : g.path("max").asInt(Math.max(1, qtd)));
            ObjectNode gg = copiar(gs.addObject(), g, "id", "name", "status", "externalCode", "index", "optionGroupType", "optionIds");
            if (!gg.hasNonNull("optionGroupType")) {
                gg.put("optionGroupType", "DEFAULT");
            }
            for (JsonNode oidNo : g.path("optionIds")) {
                JsonNode o = opcoes.get(oidNo.asText());
                if (o == null) {
                    continue;
                }
                os.add(copiar(mapper.createObjectNode(), o, "id", "status", "productId", "index", "price",
                        "externalCode", "contextModifiers", "fractions"));
                String pid = o.path("productId").asText();
                if (produtosIncluidos.add(pid)) {
                    prods.add(produto(produtos.getOrDefault(pid, mapper.createObjectNode()), pid, mapper));
                }
            }
        }
        return out;
    }

    private static ObjectNode produto(JsonNode p, String id, ObjectMapper mapper) {
        ObjectNode out = copiar(mapper.createObjectNode(), p, "id", "externalCode", "name", "description",
                "additionalInformation", "imagePath", "ean", "serving", "dietaryRestrictions", "quantity", "unit",
                "industrialized");
        out.put("id", id);
        if (!out.hasNonNull("description")) {
            out.put("description", "");
        }
        if (!out.hasNonNull("imagePath")) {
            out.put("imagePath", "");
        }
        if (!out.hasNonNull("serving")) {
            out.put("serving", "NOT_APPLICABLE");
        }
        if (!out.has("dietaryRestrictions")) {
            out.putArray("dietaryRestrictions");
        }
        return out;
    }

    // ------------------------------------------------------------------ apoio

    private void enviar(String merchantId, JsonNode payload) {
        JsonNode r = api.salvarItem(merchantId, payload);
        String lote = r != null && r.hasNonNull("batchId") ? r.get("batchId").asText() : null;
        if (lote == null) {
            return;
        }
        for (int i = 0; i < 15; i++) {
            JsonNode b = api.statusLote(merchantId, lote);
            String st = b == null ? "" : b.path("status").asText("").toUpperCase();
            if (!st.isEmpty() && !List.of("PENDING", "PROCESSING", "IN_PROGRESS", "RUNNING").contains(st)) {
                if (st.contains("FAIL") || st.contains("ERROR")) {
                    throw new Recusado("O iFood recusou o item: " + b);
                }
                return;
            }
            dormir();
        }
        log.warn("Lote {} do iFood ainda processando depois de esperar", lote);
    }

    /** Relê o cardápio do iFood até a mudança aparecer (o iFood leva alguns segundos para mostrar). */
    private Cardapio reler(String merchantId, Predicate<Cardapio> pronto) {
        Cardapio c = null;
        for (int i = 0; i < 6; i++) {
            if (i > 0 || esperaMs > 0) {
                dormir();
            }
            c = monitor.lerLoja(merchantId);
            if (pronto.test(c)) {
                return c;
            }
        }
        return c; // devolve o que tiver; a tela avisa para atualizar de novo
    }

    private String catalogoPrincipal(String merchantId) {
        List<JsonNode> cats = api.catalogos(merchantId);
        if (cats.isEmpty()) {
            throw new Recusado("Esta loja não tem catálogo no iFood");
        }
        for (JsonNode c : cats) {
            JsonNode ctx = c.path("context");
            String contexto = ctx.isArray() && !ctx.isEmpty() ? ctx.get(0).asText() : ctx.asText("DEFAULT");
            if ("DEFAULT".equalsIgnoreCase(contexto)) {
                return c.path("catalogId").asText(c.path("id").asText());
            }
        }
        return cats.get(0).path("catalogId").asText(cats.get(0).path("id").asText());
    }

    static Categoria categoriaPorNome(Cardapio c, String nome) {
        for (Catalogo cat : c.catalogos()) {
            for (Categoria k : cat.categorias()) {
                if (k.nome() != null && k.nome().strip().equalsIgnoreCase(nome)) {
                    return k;
                }
            }
        }
        return null;
    }

    static Item item(Cardapio c, String itemId) {
        for (Catalogo cat : c.catalogos()) {
            for (Categoria k : cat.categorias()) {
                for (Item i : k.itens()) {
                    if (i.id().equals(itemId)) {
                        return i;
                    }
                }
            }
        }
        return null;
    }

    private static ObjectNode produto(ObjectNode payload, String productId) {
        for (JsonNode p : payload.path("products")) {
            if (productId.equals(p.path("id").asText())) {
                return (ObjectNode) p;
            }
        }
        throw new Recusado("Produto do item não encontrado no iFood");
    }

    private static ObjectNode copiar(ObjectNode destino, JsonNode origem, String... campos) {
        for (String c : campos) {
            if (origem.hasNonNull(c)) {
                destino.set(c, origem.get(c).deepCopy());
            }
        }
        return destino;
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

    private static void preco(ObjectNode no, BigDecimal valor) {
        no.put("value", valor);
        no.put("originalValue", valor);
    }

    private static BigDecimal precoValido(BigDecimal p, boolean zeroPode) {
        if (p == null || p.signum() < 0 || (!zeroPode && p.signum() == 0)) {
            throw new Recusado(zeroPode ? "Preço inválido" : "Informe um preço maior que zero");
        }
        return p.setScale(2, RoundingMode.HALF_UP);
    }

    private static String obrigatorio(String v, String msg) {
        if (v == null || v.isBlank()) {
            throw new Recusado(msg);
        }
        return v.strip();
    }

    private void dormir() {
        if (esperaMs <= 0) {
            return;
        }
        try {
            Thread.sleep(esperaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
