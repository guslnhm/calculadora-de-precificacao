package com.precificacao.precificacao.cardapio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio;
import com.precificacao.precificacao.cardapio.CardapioModelos.Item;
import com.precificacao.precificacao.cardapio.CardapioRepository.Alteracao;
import com.precificacao.precificacao.ifood.IfoodException;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cardápio: leitura no formato real do Catalog v2, monitor de itens pausados e alterações pela tela. */
@SpringBootTest
public class CardapioTest {

    static final String LOJA = "loja-1";

    @Autowired CardapioRepository repo;
    @Autowired MonitorRepository monitorRepo;
    @Autowired GrupoWhatsappRepository grupos;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    Relogio relogio;
    ConsoleSender wpp;
    IfoodFalso ifood;
    EscritaFalsa escrita;
    MonitorItensService monitor;
    CardapioService service;

    @BeforeEach
    void preparar() throws Exception {
        repo.limparTudo();
        monitorRepo.limparTudo();
        relogio = new Relogio(Instant.parse("2026-11-06T15:00:00Z")); // 12:00 em São Paulo
        wpp = new ConsoleSender();
        ifood = new IfoodFalso(mapper);
        escrita = new EscritaFalsa(ifood);
        monitorRepo.salvarLojas(List.of(new LojaMerchant(LOJA, "Burger Moema", null)));
        grupos.salvar(null, "Zona Sul", "zs-group", false, false, true, true, List.of(LOJA));
        lojaAberta(true);

        MonitorProperties mcfg = new MonitorProperties(true, true, 30, 2, List.of("ERROR"), true, 30, true, List.of(),
                4, 60, 10, "08:00");
        ItensProperties icfg = new ItensProperties(true, 10, true, true, true, 2, List.of("Promoções da casa"));
        IfoodProperties ifp = new IfoodProperties("", "", "http://x", "America/Sao_Paulo", 5, 4);
        monitor = new MonitorItensService(icfg, mcfg, new LeitorCardapio(ifood, relogio), repo, monitorRepo, grupos,
                new EnvioWhatsapp(mcfg, wpp, monitorRepo, relogio), relogio, ifp);
        service = new CardapioService(repo, monitor, escrita, relogio);
    }

    // ------------------------------------------------------------------ leitura

    @Test
    void leOCardapioComComplementosEPrecoDoContexto() {
        Cardapio c = new LeitorCardapio(ifood, relogio).ler(LOJA);
        assertThat(c.catalogos()).hasSize(1);
        var lanches = c.catalogos().get(0).categorias().get(0);
        assertThat(lanches.nome()).isEqualTo("Lanches");
        Item x = lanches.itens().get(0);
        assertThat(x.nome()).isEqualTo("X-Bacon");
        assertThat(x.descricao()).isEqualTo("Pão, carne 180g e bacon");
        assertThat(x.imagem()).isEqualTo(LeitorCardapio.URL_FOTOS + "202411/xbacon.jpg");
        assertThat(x.preco()).isEqualByComparingTo("34.90"); // preço do contexto DEFAULT, não o base
        assertThat(x.grupos()).singleElement().satisfies(g -> {
            assertThat(g.nome()).isEqualTo("Bebida");
            assertThat(g.max()).isEqualTo(1);
            assertThat(g.opcoes()).extracting(o -> o.nome()).containsExactly("Coca 350ml", "Guaraná 350ml");
            assertThat(g.opcoes().get(1).preco()).isEqualByComparingTo("6.00");
        });
        assertThat(lanches.itens().get(1).naoVendavel()).isEqualTo("SHIFT_UNAVAILABLE");
        assertThat(c.totalItens()).isEqualTo(3);
        assertThat(c.totalPausados()).isEqualTo(1); // o não vendável
    }

    // ------------------------------------------------------------------ monitor de itens

    @Test
    void primeiraLeituraNaoAvisaEPausaDepoisAvisa() {
        ifood.statusItem("i-burger", "UNAVAILABLE"); // já estava pausado
        monitor.ciclo();
        assertThat(wpp.enviadas).isEmpty();
        assertThat(repo.pausadosAgora()).hasSize(1); // o burger: só registrado

        relogio.passar(Duration.ofMinutes(10));
        ifood.statusItem("i-xbacon", "UNAVAILABLE");
        ifood.statusOpcao("o-coca", "UNAVAILABLE");
        monitor.ciclo();
        assertThat(wpp.para("zs-group")).containsExactly(
                "⚠️ *Burger Moema*\n⏸️ *2 itens pausados*:\n• X-Bacon\n• Coca 350ml (complemento)");

        relogio.passar(Duration.ofMinutes(45));
        ifood.statusItem("i-xbacon", "AVAILABLE");
        monitor.ciclo();
        assertThat(ultima()).isEqualTo("✅ *Burger Moema*\n▶️ *Item voltou:* X-Bacon (ficou 45 min pausado)");

        // o burger que já estava pausado volta: não avisa (nunca foi avisado)
        wpp.enviadas.clear();
        ifood.statusItem("i-burger", "AVAILABLE");
        relogio.passar(Duration.ofMinutes(10));
        monitor.ciclo();
        assertThat(wpp.enviadas).isEmpty();
    }

    @Test
    void lojaFechadaSoAvisaQuandoAbrir() {
        monitor.ciclo();
        lojaAberta(false);
        relogio.passar(Duration.ofMinutes(10));
        ifood.statusItem("i-xbacon", "UNAVAILABLE");
        monitor.ciclo();
        assertThat(wpp.enviadas).isEmpty();

        relogio.passar(Duration.ofMinutes(50));
        lojaAberta(true);
        monitor.ciclo();
        assertThat(wpp.para("zs-group")).containsExactly("⚠️ *Burger Moema*\n⏸️ *Item pausado:* X-Bacon desde 12:10");
    }

    @Test
    void categoriaPausadaViraUmaLinhaSoECategoriaIgnoradaNaoConta() {
        monitor.ciclo();
        relogio.passar(Duration.ofMinutes(10));
        ifood.statusCategoria("cat-lanches", "UNAVAILABLE");
        ifood.statusItem("i-promo", "UNAVAILABLE"); // categoria ignorada pela configuração
        monitor.ciclo();
        assertThat(wpp.para("zs-group")).containsExactly("⚠️ *Burger Moema*\n⏸️ *Categoria pausada:* Lanches");
    }

    // ------------------------------------------------------------------ alterações pela tela

    @Test
    void pausarPelaTelaRegistraNaoAvisaEDaParaDesfazer() {
        monitor.ciclo();
        Alteracao a = service.alterarStatus(LOJA, "ITEM", "i-xbacon", "UNAVAILABLE", "Gustavo");
        assertThat(a.situacao()).isEqualTo("FEITA");
        assertThat(a.antes()).isEqualTo("AVAILABLE");
        assertThat(a.usuario()).isEqualTo("Gustavo");
        assertThat(escrita.chamadas).containsExactly("status ITEM i-xbacon UNAVAILABLE");
        assertThat(item(service.cardapio(LOJA, false), "i-xbacon").status()).isEqualTo("UNAVAILABLE");

        // o próximo ciclo lê do iFood já pausado: não avisa, porque fomos nós que pausamos
        relogio.passar(Duration.ofMinutes(10));
        monitor.ciclo();
        assertThat(wpp.enviadas).isEmpty();

        Alteracao volta = service.desfazer(a.id(), "Chefe");
        assertThat(volta.situacao()).isEqualTo("FEITA");
        assertThat(volta.depois()).isEqualTo("AVAILABLE");
        assertThat(volta.desfazId()).isEqualTo(a.id());
        assertThat(repo.alteracao(a.id()).orElseThrow().situacao()).isEqualTo("DESFEITA");
        assertThatThrownBy(() -> service.desfazer(a.id(), "Chefe")).hasMessageContaining("não pode ser desfeita");

        relogio.passar(Duration.ofMinutes(10));
        monitor.ciclo();
        assertThat(wpp.enviadas).isEmpty(); // a volta também não vira alerta
    }

    @Test
    void mudarPrecoDeItemEComplemento() {
        monitor.ciclo();
        Alteracao a = service.alterarPreco(LOJA, "ITEM", "i-xbacon", new BigDecimal("36.5"), "Gustavo");
        assertThat(a.antes()).isEqualTo("34.90");
        assertThat(a.depois()).isEqualTo("36.50");
        Alteracao b = service.alterarPreco(LOJA, "COMPLEMENTO", "o-guarana", new BigDecimal("0"), "Gustavo");
        assertThat(b.situacao()).isEqualTo("FEITA");

        Cardapio c = service.cardapio(LOJA, false);
        assertThat(item(c, "i-xbacon").preco()).isEqualByComparingTo("36.50");
        assertThat(item(c, "i-xbacon").grupos().get(0).opcoes().get(1).preco()).isEqualByComparingTo("0");
        assertThat(service.alteracoes(LOJA)).hasSize(2);

        assertThatThrownBy(() -> service.alterarPreco(LOJA, "ITEM", "i-xbacon", BigDecimal.ZERO, "x"))
                .hasMessageContaining("R$ 0,00");
        assertThatThrownBy(() -> service.alterarStatus(LOJA, "ITEM", "nao-existe", "UNAVAILABLE", "x"))
                .hasMessageContaining("Atualizar");
    }

    @Test
    void falhaNoIfoodFicaRegistradaENaoMudaOCardapio() {
        monitor.ciclo();
        escrita.falhar = new IfoodException(400, "PATCH /items/price", "{\"message\":\"invalid price\"}");
        Alteracao a = service.alterarPreco(LOJA, "ITEM", "i-xbacon", new BigDecimal("40"), "Gustavo");
        assertThat(a.situacao()).isEqualTo("FALHOU");
        assertThat(a.erro()).contains("400");
        assertThat(item(service.cardapio(LOJA, false), "i-xbacon").preco()).isEqualByComparingTo("34.90");
    }

    @Test
    void resumoDasLojas() {
        monitor.ciclo();
        assertThat(repo.resumoLojas()).singleElement().satisfies(l -> {
            assertThat(l.get("loja")).isEqualTo("Burger Moema");
            assertThat(((Number) l.get("itens")).intValue()).isEqualTo(3);
            assertThat(((Number) l.get("pausados")).intValue()).isEqualTo(1);
        });
    }

    // ------------------------------------------------------------------ apoio

    void lojaAberta(boolean aberta) {
        jdbc.update("""
                INSERT INTO ifood_status_atual (merchant_id, operacao, canal, estado, desde, verificado_em)
                VALUES (?, 'DELIVERY', 'IFOOD', ?, ?, ?)
                ON CONFLICT (merchant_id, operacao, canal) DO UPDATE SET estado = EXCLUDED.estado
                """, LOJA, aberta ? "OK" : "CLOSED", Timestamp.from(relogio.instant()), Timestamp.from(relogio.instant()));
    }

    String ultima() {
        List<String> l = wpp.para("zs-group");
        return l.get(l.size() - 1);
    }

    static Item item(Cardapio c, String id) {
        return c.catalogos().stream().flatMap(k -> k.categorias().stream()).flatMap(k -> k.itens().stream())
                .filter(i -> i.id().equals(id)).findFirst().orElseThrow();
    }

    /** iFood falso com respostas no formato real do Catalog v2, que dá para alterar entre um ciclo e outro. */
    public static class IfoodFalso implements LeitorCardapio.Fonte {
        final ObjectMapper mapper;
        final Map<String, ObjectNode> itensPorCategoria = new HashMap<>();
        final List<ObjectNode> categorias = new ArrayList<>();

        public IfoodFalso(ObjectMapper mapper) throws Exception {
            this.mapper = mapper;
            categorias.add((ObjectNode) mapper.readTree("{\"id\":\"cat-lanches\",\"name\":\"Lanches\",\"status\":\"AVAILABLE\",\"index\":0}"));
            categorias.add((ObjectNode) mapper.readTree("{\"id\":\"cat-promo\",\"name\":\"Promoções da casa\",\"status\":\"AVAILABLE\",\"index\":1}"));
            itensPorCategoria.put("cat-lanches", (ObjectNode) mapper.readTree("""
                    {
                      "items": [
                        {"id":"i-xbacon","productId":"p-xbacon","status":"AVAILABLE","index":0,"externalCode":"XB",
                         "price":{"value":32.9,"originalValue":32.9},
                         "contextModifiers":[{"catalogContext":"DEFAULT","status":"AVAILABLE","price":{"value":34.9,"originalValue":34.9}}]},
                        {"id":"i-burger","productId":"p-burger","status":"AVAILABLE","index":1,
                         "price":{"value":29.9,"originalValue":29.9}}
                      ],
                      "products": [
                        {"id":"p-xbacon","name":"X-Bacon","description":"Pão, carne 180g e bacon","imagePath":"202411/xbacon.jpg",
                         "optionGroups":[{"id":"g-bebida","min":0,"max":1}]},
                        {"id":"p-burger","name":"Burger Clássico","description":"","imagePath":""},
                        {"id":"p-coca","name":"Coca 350ml"},
                        {"id":"p-guarana","name":"Guaraná 350ml"}
                      ],
                      "optionGroups": [
                        {"id":"g-bebida","name":"Bebida","status":"AVAILABLE","optionIds":["o-coca","o-guarana"]}
                      ],
                      "options": [
                        {"id":"o-coca","productId":"p-coca","status":"AVAILABLE","price":{"value":6.5,"originalValue":6.5}},
                        {"id":"o-guarana","productId":"p-guarana","status":"AVAILABLE","price":{"value":6,"originalValue":6}}
                      ]
                    }
                    """));
            itensPorCategoria.put("cat-promo", (ObjectNode) mapper.readTree("""
                    {"items":[{"id":"i-promo","productId":"p-promo","status":"AVAILABLE","price":{"value":19.9}}],
                     "products":[{"id":"p-promo","name":"Combo do dia"}],"optionGroups":[],"options":[]}
                    """));
        }

        void statusItem(String id, String status) {
            itensPorCategoria.values().forEach(r -> r.path("items").forEach(i -> {
                if (i.path("id").asText().equals(id)) {
                    ((ObjectNode) i).put("status", status);
                    i.path("contextModifiers").forEach(m -> ((ObjectNode) m).put("status", status));
                }
            }));
        }

        void precoItem(String id, BigDecimal valor) {
            itensPorCategoria.values().forEach(r -> r.path("items").forEach(i -> {
                if (i.path("id").asText().equals(id)) {
                    ((ObjectNode) i.path("price")).put("value", valor);
                    i.path("contextModifiers").forEach(m -> ((ObjectNode) m.path("price")).put("value", valor));
                }
            }));
        }

        void statusOpcao(String id, String status) {
            itensPorCategoria.values().forEach(r -> r.path("options").forEach(o -> {
                if (o.path("id").asText().equals(id)) {
                    ((ObjectNode) o).put("status", status);
                }
            }));
        }

        void precoOpcao(String id, BigDecimal valor) {
            itensPorCategoria.values().forEach(r -> r.path("options").forEach(o -> {
                if (o.path("id").asText().equals(id)) {
                    ((ObjectNode) o.path("price")).put("value", valor);
                }
            }));
        }

        void statusCategoria(String id, String status) {
            categorias.stream().filter(k -> k.path("id").asText().equals(id)).forEach(k -> k.put("status", status));
        }

        @Override
        public List<JsonNode> catalogos(String merchantId) {
            try {
                return List.of(mapper.readTree("{\"catalogId\":\"cat-1\",\"context\":[\"DEFAULT\"],\"status\":\"AVAILABLE\"}"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public List<JsonNode> categorias(String merchantId, String catalogId) {
            return List.copyOf(categorias.stream().map(k -> (JsonNode) k.deepCopy()).toList());
        }

        @Override
        public JsonNode itensDaCategoria(String merchantId, String categoryId) {
            return itensPorCategoria.get(categoryId).deepCopy();
        }

        @Override
        public JsonNode naoVendaveis(String merchantId, String catalogId) {
            try {
                return mapper.readTree("{\"categories\":[{\"id\":\"cat-lanches\",\"unsellableItems\":"
                        + "[{\"id\":\"i-burger\",\"productId\":\"p-burger\",\"restrictions\":[\"SHIFT_UNAVAILABLE\"]}]}]}");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Escrita falsa: aplica a mudança no iFood falso (como o iFood real faria). */
    public static class EscritaFalsa implements CardapioService.Escrita {
        final IfoodFalso ifood;
        public final List<String> chamadas = new ArrayList<>();
        public RuntimeException falhar;

        public EscritaFalsa(IfoodFalso ifood) {
            this.ifood = ifood;
        }

        @Override
        public void status(String merchantId, String tipo, String id, String status) {
            if (falhar != null) {
                throw falhar;
            }
            chamadas.add("status " + tipo + " " + id + " " + status);
            if ("ITEM".equals(tipo)) {
                ifood.statusItem(id, status);
            } else {
                ifood.statusOpcao(id, status);
            }
        }

        @Override
        public void preco(String merchantId, String tipo, String id, BigDecimal valor) {
            if (falhar != null) {
                throw falhar;
            }
            chamadas.add("preco " + tipo + " " + id + " " + valor);
            if ("ITEM".equals(tipo)) {
                ifood.precoItem(id, valor);
            } else {
                ifood.precoOpcao(id, valor);
            }
        }
    }

    public static class Relogio extends Clock {
        private Instant agora;

        public Relogio(Instant inicio) {
            agora = inicio;
        }

        public void passar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
