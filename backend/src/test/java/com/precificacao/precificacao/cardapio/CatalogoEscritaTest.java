package com.precificacao.precificacao.cardapio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Categoria;
import com.precificacao.precificacao.cardapio.CardapioModelos.Item;
import com.precificacao.precificacao.cardapio.CardapioModelos.Opcao;
import com.precificacao.precificacao.cardapio.CatalogoEscrita.NovoComplemento;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.monitor.EnvioWhatsapp;
import com.precificacao.precificacao.monitor.GrupoWhatsappRepository;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorProperties;
import com.precificacao.precificacao.monitor.MonitorRepository;
import com.precificacao.precificacao.whatsapp.ConsoleSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os 3 cenários de Catalog da homologação feitos pelo site, contra um iFood falso que aplica cada PUT. */
@SpringBootTest
class CatalogoEscritaTest {

    static final String LOJA = "loja-1";

    @Autowired CardapioRepository repo;
    @Autowired MonitorRepository monitorRepo;
    @Autowired GrupoWhatsappRepository grupos;
    @Autowired ObjectMapper mapper;

    CardapioTest.IfoodFalso ifood;
    ApiFalsa api;
    CardapioService cardapio;
    CatalogoEscrita escrita;

    @BeforeEach
    void preparar() throws Exception {
        repo.limparTudo();
        monitorRepo.limparTudo();
        monitorRepo.salvarLojas(List.of(new LojaMerchant(LOJA, "Açaí Teste", null)));
        var relogio = new CardapioTest.Relogio(Instant.parse("2026-11-06T18:00:00Z"));
        ifood = new CardapioTest.IfoodFalso(mapper);
        api = new ApiFalsa(ifood, mapper);
        MonitorProperties mcfg = new MonitorProperties(true, true, 30, 2, List.of("ERROR"), true, 30, true, List.of(),
                4, 60, 10, "08:00");
        MonitorItensService monitor = new MonitorItensService(new ItensProperties(true, 10, true, true, true, 2, List.of()),
                mcfg, new LeitorCardapio(ifood, relogio), repo, monitorRepo, grupos,
                new EnvioWhatsapp(mcfg, new ConsoleSender(), monitorRepo, relogio), relogio,
                new IfoodProperties("", "", "http://x", "America/Sao_Paulo", 5, 4));
        cardapio = new CardapioService(repo, monitor, new CardapioTest.EscritaFalsa(ifood), relogio);
        escrita = new CatalogoEscrita(api, monitor, cardapio, mapper, 0);
    }

    @Test
    void cenario1CategoriaEItemComFoto() {
        Cardapio c = escrita.criarCategoria(LOJA, "Teste Homologação");
        Categoria cat = CatalogoEscrita.categoriaPorNome(c, "Teste Homologação");
        assertThat(cat).isNotNull();
        // de novo com o mesmo nome: não cria outra
        escrita.criarCategoria(LOJA, "teste homologação");
        assertThat(api.categoriasCriadas).isEqualTo(1);

        String foto = escrita.enviarFoto(LOJA, new byte[]{1, 2, 3}, "image/jpeg");
        c = escrita.criarItem(LOJA, cat.id(), "Produto Teste", "Feito pelo site", new BigDecimal("25.9"), foto);
        Item item = CatalogoEscrita.categoriaPorNome(c, "Teste Homologação").itens().get(0);
        assertThat(item.nome()).isEqualTo("Produto Teste");
        assertThat(item.preco()).isEqualByComparingTo("25.90");
        assertThat(item.status()).isEqualTo("AVAILABLE");
        assertThat(item.imagem()).endsWith(foto);

        JsonNode put = api.puts.get(0);
        assertThat(put.path("item").path("categoryId").asText()).isEqualTo(cat.id());
        assertThat(put.path("item").path("price").path("originalValue").decimalValue()).isEqualByComparingTo("25.90");
        assertThat(put.path("products").get(0).path("imagePath").asText()).isEqualTo(foto);

        assertThatThrownBy(() -> escrita.enviarFoto(LOJA, new byte[]{1}, "image/gif")).hasMessageContaining("JPG ou PNG");
        assertThatThrownBy(() -> escrita.criarItem(LOJA, cat.id(), "X", null, BigDecimal.ZERO, null))
                .hasMessageContaining("maior que zero");
    }

    @Test
    void cenario2GrupoDeComplementos() {
        String itemId = criarProdutoTeste();
        Cardapio c = escrita.criarComplementos(LOJA, itemId, "Adicionais", 0, 2, List.of(
                new NovoComplemento("Granola", new BigDecimal("2.50"), "f/granola.jpg"),
                new NovoComplemento("Leite Ninho", new BigDecimal("3"), "f/ninho.jpg")));
        Item item = CatalogoEscrita.item(c, itemId);
        assertThat(item.grupos()).singleElement().satisfies(g -> {
            assertThat(g.nome()).isEqualTo("Adicionais");
            assertThat(g.max()).isEqualTo(2);
            assertThat(g.opcoes()).extracting(Opcao::nome).containsExactly("Granola", "Leite Ninho");
            assertThat(g.opcoes()).extracting(Opcao::status).containsOnly("AVAILABLE");
            assertThat(g.opcoes().get(1).preco()).isEqualByComparingTo("3.00");
        });
        JsonNode put = api.puts.get(api.puts.size() - 1);
        assertThat(put.path("products")).hasSize(3);
        assertThat(put.path("optionGroups").get(0).path("optionIds")).hasSize(2);
    }

    @Test
    void cenario3AlterarItemEComplementoSemPerderNada() {
        String itemId = criarProdutoTeste();
        Cardapio c = escrita.criarComplementos(LOJA, itemId, "Adicionais", null, null, List.of(
                new NovoComplemento("Granola", new BigDecimal("2.50"), "f/granola.jpg"),
                new NovoComplemento("Leite Ninho", new BigDecimal("3.00"), "f/ninho.jpg")));
        List<Opcao> ops = CatalogoEscrita.item(c, itemId).grupos().get(0).opcoes();

        // item: nome e foto (PUT) — os complementos continuam lá
        c = escrita.editarItem(LOJA, itemId, "Produto Teste Alterado", null, "f/v2.jpg");
        Item item = CatalogoEscrita.item(c, itemId);
        assertThat(item.nome()).isEqualTo("Produto Teste Alterado");
        assertThat(item.imagem()).endsWith("f/v2.jpg");
        assertThat(item.grupos().get(0).opcoes()).hasSize(2);
        assertThat(item.preco()).isEqualByComparingTo("25.90");

        // preço do item e pausa do 1º complemento continuam pelos PATCH
        cardapio.alterarPreco(LOJA, "ITEM", itemId, new BigDecimal("27.90"), "Gustavo");
        cardapio.alterarStatus(LOJA, "COMPLEMENTO", ops.get(0).id(), "UNAVAILABLE", "Gustavo");

        // 2º complemento: nome e foto (PUT) — a pausa do 1º e o preço novo do item não se perdem
        c = escrita.editarComplemento(LOJA, itemId, ops.get(1).id(), "Leite Ninho Extra", "f/ninho2.jpg");
        cardapio.alterarPreco(LOJA, "COMPLEMENTO", ops.get(1).id(), new BigDecimal("3.50"), "Gustavo");
        cardapio.alterarStatus(LOJA, "COMPLEMENTO", ops.get(1).id(), "UNAVAILABLE", "Gustavo");

        c = cardapio.cardapio(LOJA, true);
        item = CatalogoEscrita.item(c, itemId);
        assertThat(item.preco()).isEqualByComparingTo("27.90");
        List<Opcao> depois = item.grupos().get(0).opcoes();
        assertThat(depois.get(0).status()).isEqualTo("UNAVAILABLE");
        assertThat(depois.get(1).nome()).isEqualTo("Leite Ninho Extra");
        assertThat(depois.get(1).preco()).isEqualByComparingTo("3.50");
        assertThat(depois.get(1).status()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void montaOPutAPartirDoGetDoIfood() {
        JsonNode bruto = ifood.itensDaCategoria(LOJA, "cat-lanches");
        ObjectNode p = CatalogoEscrita.montarPayload(bruto, "i-xbacon", "cat-lanches", mapper);
        assertThat(p.path("item").path("categoryId").asText()).isEqualTo("cat-lanches");
        assertThat(p.path("item").path("type").asText()).isEqualTo("DEFAULT");
        assertThat(p.path("item").path("contextModifiers")).hasSize(1); // preço do Delivery não se perde
        assertThat(p.path("products")).extracting(n -> n.path("name").asText())
                .containsExactly("X-Bacon", "Coca 350ml", "Guaraná 350ml");
        JsonNode ref = p.path("products").get(0).path("optionGroups").get(0);
        assertThat(ref.path("id").asText()).isEqualTo("g-bebida");
        assertThat(ref.path("max").asInt()).isEqualTo(1);
        assertThat(p.path("options")).hasSize(2);
        assertThat(p.path("products").get(1).path("serving").asText()).isEqualTo("NOT_APPLICABLE");
        assertThatThrownBy(() -> CatalogoEscrita.montarPayload(bruto, "nao-existe", "cat-lanches", mapper))
                .hasMessageContaining("ainda não mostra");
    }

    String criarProdutoTeste() {
        Cardapio c = escrita.criarCategoria(LOJA, "Teste Homologação");
        Categoria cat = CatalogoEscrita.categoriaPorNome(c, "Teste Homologação");
        c = escrita.criarItem(LOJA, cat.id(), "Produto Teste", "", new BigDecimal("25.90"), "f/produto.jpg");
        return CatalogoEscrita.categoriaPorNome(c, "Teste Homologação").itens().get(0).id();
    }

    /** "iFood" em memória: aplica o PUT /items na resposta do GET /categories/{id}/items. */
    static class ApiFalsa implements CatalogoEscrita.Api {
        final CardapioTest.IfoodFalso ifood;
        final ObjectMapper mapper;
        final List<JsonNode> puts = new ArrayList<>();
        int categoriasCriadas;
        int fotos;

        ApiFalsa(CardapioTest.IfoodFalso ifood, ObjectMapper mapper) {
            this.ifood = ifood;
            this.mapper = mapper;
        }

        @Override
        public List<JsonNode> catalogos(String merchantId) {
            return ifood.catalogos(merchantId);
        }

        @Override
        public List<JsonNode> categorias(String merchantId, String catalogId) {
            return ifood.categorias(merchantId, catalogId);
        }

        @Override
        public JsonNode criarCategoria(String merchantId, String catalogId, String nome) {
            categoriasCriadas++;
            ObjectNode k = mapper.createObjectNode();
            k.put("id", UUID.randomUUID().toString());
            k.put("name", nome);
            k.put("status", "AVAILABLE");
            k.put("index", ifood.categorias.size());
            ifood.categorias.add(k);
            ObjectNode vazio = mapper.createObjectNode();
            vazio.putArray("items");
            vazio.putArray("products");
            vazio.putArray("optionGroups");
            vazio.putArray("options");
            ifood.itensPorCategoria.put(k.get("id").asText(), vazio);
            return k;
        }

        @Override
        public JsonNode itensDaCategoria(String merchantId, String categoryId) {
            return ifood.itensDaCategoria(merchantId, categoryId);
        }

        @Override
        public JsonNode salvarItem(String merchantId, JsonNode payload) {
            puts.add(payload.deepCopy());
            ObjectNode resp = ifood.itensPorCategoria.get(payload.path("item").path("categoryId").asText());
            substituir((ArrayNode) resp.get("items"), payload.get("item"));
            payload.path("products").forEach(p -> substituir((ArrayNode) resp.get("products"), p));
            payload.path("optionGroups").forEach(g -> substituir((ArrayNode) resp.get("optionGroups"), g));
            payload.path("options").forEach(o -> substituir((ArrayNode) resp.get("options"), o));
            return mapper.createObjectNode().put("batchId", "lote-" + puts.size());
        }

        @Override
        public JsonNode statusLote(String merchantId, String batchId) {
            return mapper.createObjectNode().put("status", "COMPLETED");
        }

        @Override
        public String enviarFoto(String merchantId, byte[] conteudo, String tipo) {
            return "202611/foto-" + (++fotos) + ".jpg";
        }

        private static void substituir(ArrayNode lista, JsonNode novo) {
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).path("id").asText().equals(novo.path("id").asText())) {
                    lista.set(i, novo.deepCopy());
                    return;
                }
            }
            lista.add(novo.deepCopy());
        }
    }
}
