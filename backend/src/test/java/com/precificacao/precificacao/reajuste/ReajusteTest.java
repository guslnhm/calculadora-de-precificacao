package com.precificacao.precificacao.reajuste;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.cardapio.CardapioModelos.Item;
import com.precificacao.precificacao.cardapio.CardapioRepository;
import com.precificacao.precificacao.cardapio.CardapioService;
import com.precificacao.precificacao.cardapio.CardapioTest;
import com.precificacao.precificacao.cardapio.ItensProperties;
import com.precificacao.precificacao.cardapio.LeitorCardapio;
import com.precificacao.precificacao.cardapio.MonitorItensService;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.monitor.EnvioWhatsapp;
import com.precificacao.precificacao.monitor.GrupoWhatsappRepository;
import com.precificacao.precificacao.monitor.MonitorModelos.LojaMerchant;
import com.precificacao.precificacao.monitor.MonitorProperties;
import com.precificacao.precificacao.monitor.MonitorRepository;
import com.precificacao.precificacao.reajuste.ReajusteRepository.Lote;
import com.precificacao.precificacao.reajuste.ReajusteService.EntidadeIfood;
import com.precificacao.precificacao.reajuste.ReajusteService.LinhaPrevia;
import com.precificacao.precificacao.reajuste.ReajusteService.Previa;
import com.precificacao.precificacao.reajuste.ReajusteService.VinculoRequest;
import com.precificacao.precificacao.repository.ItemRepository;
import com.precificacao.precificacao.repository.LojaRepository;
import com.precificacao.precificacao.service.precificacao.CalculadoraPrecoIfood;
import com.precificacao.precificacao.whatsapp.ConsoleSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReajusteTest {

    static final String M = "loja-1";

    @Autowired CardapioRepository cardapioRepo;
    @Autowired MonitorRepository monitorRepo;
    @Autowired GrupoWhatsappRepository grupos;
    @Autowired ReajusteRepository repo;
    @Autowired LojaRepository lojas;
    @Autowired ItemRepository itens;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired com.precificacao.precificacao.service.ItemService itemService;

    CardapioTest.Relogio relogio;
    CardapioTest.IfoodFalso ifood;
    CardapioTest.EscritaFalsa escrita;
    CardapioService cardapio;
    ReajusteService service;
    Loja loja;
    com.precificacao.precificacao.entity.Item xBacon;
    com.precificacao.precificacao.entity.Item burger;

    @BeforeEach
    void preparar() throws Exception {
        repo.limparTudo();
        cardapioRepo.limparTudo();
        monitorRepo.limparTudo();
        relogio = new CardapioTest.Relogio(Instant.parse("2026-11-06T15:00:00Z"));
        ifood = new CardapioTest.IfoodFalso(mapper);
        escrita = new CardapioTest.EscritaFalsa(ifood);
        monitorRepo.salvarLojas(List.of(new LojaMerchant(M, "Burger Moema", null)));

        MonitorProperties mcfg = new MonitorProperties(true, true, 30, 2, List.of("ERROR"), true, 30, true, List.of(),
                4, 60, 10, "08:00");
        MonitorItensService monitor = new MonitorItensService(new ItensProperties(true, 10, true, true, true, 2, List.of()),
                mcfg, new LeitorCardapio(ifood, relogio), cardapioRepo, monitorRepo, grupos,
                new EnvioWhatsapp(mcfg, new ConsoleSender(), monitorRepo, relogio), relogio,
                new IfoodProperties("", "", "http://x", "America/Sao_Paulo", 5, 4));
        cardapio = new CardapioService(cardapioRepo, monitor, escrita, relogio);
        service = new ReajusteService(repo, cardapioRepo, cardapio, lojas, itens, jdbc, relogio, Runnable::run);

        loja = new Loja();
        loja.setNome("Burger Rede");
        loja.setPercentualImposto(new BigDecimal("10"));
        loja.setPercentualTaxaIfood(new BigDecimal("12"));
        loja.setPercentualTaxaRepasse(new BigDecimal("3.2"));
        loja.setPercentualLucro(new BigDecimal("20"));
        loja.setAtivo(true);
        loja.setCriadoEm(LocalDateTime.now());
        loja.setAtualizadoEm(LocalDateTime.now());
        loja = lojas.save(loja);
        xBacon = item("X Bacon", "12.00");
        burger = item("Burger Classico", "10.50");
        item("Coca-Cola 350ml", "3.00");

        cardapio.cardapio(M, true); // 1ª leitura do cardápio da loja no iFood
    }

    com.precificacao.precificacao.entity.Item item(String nome, String cmv) {
        var i = new com.precificacao.precificacao.entity.Item();
        i.setLoja(loja);
        i.setNomeItem(nome);
        i.setCmv(new BigDecimal(cmv));
        i.setAtivo(true);
        i.setCriadoEm(LocalDateTime.now());
        i.setAtualizadoEm(LocalDateTime.now());
        return itens.save(i);
    }

    BigDecimal precoCalculado(String cmv) {
        return CalculadoraPrecoIfood.calcular(new BigDecimal(cmv), null,
                CalculadoraPrecoIfood.Percentuais.daLoja(loja), CalculadoraPrecoIfood.Regras.daLoja(loja)).precoFinal();
    }

    @Test
    void lojaDoIfoodPrecisaEstarLigada() {
        assertThatThrownBy(() -> service.vinculos(loja.getId(), M)).hasMessageContaining("não está ligada");
        assertThat(service.vincularLojas(loja.getId(), List.of(M))).singleElement()
                .satisfies(l -> assertThat(l.nome()).isEqualTo("Burger Moema"));
        assertThat(service.todasLojasIfood()).singleElement().satisfies(l -> assertThat(l.lojaId()).isEqualTo(loja.getId()));
    }

    @Test
    void sugereVinculosPeloNomeEAceitaDeUmaVez() {
        service.vincularLojas(loja.getId(), List.of(M));
        var v = service.vinculos(loja.getId(), M);
        EntidadeIfood xb = entidade(v.entidades(), "X-Bacon");
        assertThat(xb.sugestao().itemId()).isEqualTo(xBacon.getId());
        assertThat(xb.sugestao().similaridade()).isEqualTo(1.0);
        assertThat(entidade(v.entidades(), "Burger Clássico").sugestao().itemId()).isEqualTo(burger.getId());
        // complemento só com nome praticamente igual ("Coca 350ml" × "Coca-Cola 350ml" não basta)
        assertThat(entidade(v.entidades(), "Coca 350ml").sugestao()).isNull();
        assertThat(v.itensCalculadora()).extracting(i -> i.nome()).contains("X Bacon", "Burger Classico");

        assertThat(service.aceitarSugestoes(loja.getId(), M, 0.9, "Gustavo")).isEqualTo(2);
        var depois = service.vinculos(loja.getId(), M);
        assertThat(entidade(depois.entidades(), "X-Bacon").vinculo().origem()).isEqualTo("AUTO");
        assertThat(entidade(depois.entidades(), "X-Bacon").sugestao()).isNull();
    }

    @Test
    void previaPublicaEDesfaz() {
        service.vincularLojas(loja.getId(), List.of(M));
        service.aceitarSugestoes(loja.getId(), M, 0.9, "Gustavo");
        // o burger o consultor decide não reajustar
        service.salvarVinculos(loja.getId(), M, List.of(new VinculoRequest("ITEM", "i-burger", "Burger Clássico", null, true)), "Gustavo");

        Previa p = service.previa(loja.getId(), "CALCULADO");
        assertThat(p.linhas()).singleElement().satisfies(l -> {
            assertThat(l.ifoodNome()).isEqualTo("X-Bacon");
            assertThat(l.itemNome()).isEqualTo("X Bacon");
            assertThat(l.precoAtual()).isEqualByComparingTo("34.90");
            assertThat(l.precoNovo()).isEqualByComparingTo(precoCalculado("12.00"));
            assertThat(l.muda()).isTrue();
            assertThat(l.variacao()).isNotNull();
            assertThat(l.margemNova()).isNotNull();
        });
        assertThat(p.avisos()).anyMatch(a -> a.contains("sem vínculo")); // o i-promo

        LinhaPrevia linha = p.linhas().get(0);
        Lote lote = service.publicar(loja.getId(), "CALCULADO", List.of(linha.chave()), "Gustavo");
        Lote fim = service.lote(lote.id()).orElseThrow();
        assertThat(fim.situacao()).isEqualTo("CONCLUIDO");
        assertThat(fim.total()).isEqualTo(1);
        assertThat(fim.feitas()).isEqualTo(1);
        assertThat(escrita.chamadas).containsExactly("preco ITEM i-xbacon " + linha.precoNovo());
        assertThat(item(cardapio.cardapio(M, false), "i-xbacon").preco()).isEqualByComparingTo(linha.precoNovo());
        assertThat(itens.findById(xBacon.getId()).orElseThrow().getPrecoVendaAtualIfood())
                .isEqualByComparingTo(linha.precoNovo());
        assertThat(service.alteracoesDoLote(lote.id())).singleElement()
                .satisfies(a -> assertThat(a.get("antes")).isEqualTo("34.90"));

        // a prévia agora não vê mais mudança
        assertThat(service.previa(loja.getId(), "CALCULADO").linhas().get(0).muda()).isFalse();
        assertThatThrownBy(() -> service.publicar(loja.getId(), "CALCULADO", List.of(linha.chave()), "x"))
                .hasMessageContaining("Nenhum preço");

        Lote desfeito = service.desfazer(lote.id(), "Chefe");
        assertThat(service.lote(desfeito.id()).orElseThrow()).satisfies(l -> {
            assertThat(l.situacao()).isEqualTo("DESFEITO");
            assertThat(l.desfeitas()).isEqualTo(1);
            assertThat(l.desfeitoPor()).isEqualTo("Chefe");
        });
        assertThat(item(cardapio.cardapio(M, false), "i-xbacon").preco()).isEqualByComparingTo("34.90");
        assertThat(itens.findById(xBacon.getId()).orElseThrow().getPrecoVendaAtualIfood()).isEqualByComparingTo("34.90");
        assertThatThrownBy(() -> service.desfazer(lote.id(), "x")).hasMessageContaining("concluído");
    }

    @Test
    void regrasDaLojaMudamOPreco() {
        service.vincularLojas(loja.getId(), List.of(M));
        service.aceitarSugestoes(loja.getId(), M, 0.9, "Gustavo");
        BigDecimal base = service.previa(loja.getId(), "CALCULADO").linhas().stream()
                .filter(l -> l.ifoodId().equals("i-xbacon")).findFirst().orElseThrow().precoNovo();
        service.salvarRegras(loja.getId(), "CUPOM_5", "NOVENTA", null);
        BigDecimal comCupom = service.previa(loja.getId(), "CALCULADO").linhas().stream()
                .filter(l -> l.ifoodId().equals("i-xbacon")).findFirst().orElseThrow().precoNovo();
        assertThat(comCupom).isGreaterThan(base.add(new BigDecimal("4.99")));
        assertThat(comCupom.remainder(BigDecimal.ONE)).isEqualByComparingTo("0.90");
        assertThatThrownBy(() -> service.salvarRegras(loja.getId(), "CUPOM_3", null, null)).hasMessageContaining("inválida");
    }

    @Test
    void fonteSalvaUsaOPrecoDaCalculadora() {
        service.vincularLojas(loja.getId(), List.of(M));
        service.aceitarSugestoes(loja.getId(), M, 0.9, "Gustavo");
        Previa p = service.previa(loja.getId(), "SALVO");
        assertThat(p.linhas()).allSatisfy(l -> {
            assertThat(l.precoNovo()).isNull();
            assertThat(l.aviso()).contains("Sem preço");
        });
        jdbc.update("UPDATE itens SET preco_venda_atual_ifood = 36.90 WHERE id = ?", xBacon.getId());
        assertThat(service.previa(loja.getId(), "SALVO").linhas()).anySatisfy(l -> {
            assertThat(l.ifoodId()).isEqualTo("i-xbacon");
            assertThat(l.precoNovo()).isEqualByComparingTo("36.90");
        });
    }

    @Test
    void importaItensDoIfoodLigadosESemDuplicar() {
        var r = service.importarDoIfood(loja.getId(), M, "Gustavo"); // liga a loja do iFood na hora
        assertThat(r.lojas()).containsExactly("Burger Moema");
        assertThat(r.ligadosAExistentes()).isEqualTo(2); // X-Bacon e Burger Clássico já existiam com nome parecido
        assertThat(r.criados()).isEqualTo(1);            // Combo do dia
        var combo = itens.findByLojaIdAndAtivoTrue(loja.getId()).stream()
                .filter(i -> i.getNomeItem().equals("Combo do dia")).findFirst().orElseThrow();
        assertThat(combo.getCmv()).isEqualByComparingTo("0");
        assertThat(combo.getPrecoVendaAtualIfood()).isEqualByComparingTo("19.90");

        var de_novo = service.importarDoIfood(loja.getId(), null, "Gustavo");
        assertThat(de_novo.criados()).isZero();
        assertThat(de_novo.jaLigados()).isEqualTo(3);
        // complementos não entram
        assertThat(service.vinculos(loja.getId(), M).entidades()).filteredOn(e -> e.tipo().equals("COMPLEMENTO"))
                .allSatisfy(e -> assertThat(e.vinculo()).isNull());
    }

    @Test
    void calculaComCadaCupomESalvaParaARevisao() {
        service.importarDoIfood(loja.getId(), M, "Gustavo");
        var precos = service.precos(loja.getId());
        var xb = precos.itens().stream().filter(i -> i.nome().equals("X Bacon")).findFirst().orElseThrow();
        assertThat(xb.opcoes().get("BASE")).isEqualByComparingTo(precoCalculado("12.00"));
        assertThat(xb.opcoes().get("CUPOM_5")).isEqualByComparingTo(precoCalculado("12.00").add(new BigDecimal("5")));
        assertThat(xb.precoIfood()).isEqualByComparingTo("34.90");
        assertThat(xb.sugerido()).isEqualTo("BASE");
        assertThat(xb.margens().get("CUPOM_10")).isGreaterThan(xb.margens().get("BASE"));
        assertThat(precos.itens().stream().filter(i -> i.nome().equals("Combo do dia")).findFirst().orElseThrow().semCusto())
                .isTrue();

        BigDecimal escolhido = xb.opcoes().get("CUPOM_8");
        assertThat(service.salvarPrecos(loja.getId(), List.of(new ReajusteService.SalvarPreco(xb.itemId(), escolhido))))
                .isEqualTo(1);
        assertThat(service.previa(loja.getId(), "SALVO").linhas()).anySatisfy(l -> {
            assertThat(l.ifoodId()).isEqualTo("i-xbacon");
            assertThat(l.precoNovo()).isEqualByComparingTo(escolhido);
            assertThat(l.muda()).isTrue();
        });
        assertThatThrownBy(() -> service.salvarPrecos(loja.getId(), List.of(new ReajusteService.SalvarPreco(999999L, BigDecimal.TEN))))
                .hasMessageContaining("não é desta loja");
    }

    @Test
    void planilhaDeCustosVaiEVoltaSemDuplicar() throws Exception {
        service.importarDoIfood(loja.getId(), M, "Gustavo");
        int antes = itens.findByLojaIdAndAtivoTrue(loja.getId()).size();
        byte[] xlsx = itemService.planilhaCustos(loja.getId());
        byte[] preenchida;
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx));
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Custo (R$)");
            assertThat(sheet.isColumnHidden(2)).isTrue();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                var row = sheet.getRow(r);
                if (row.getCell(0).getStringCellValue().equals("Combo do dia")) {
                    row.getCell(0).setCellValue("Combo do Dia (promo)"); // cliente mexeu no nome: o código resolve
                    row.getCell(1).setCellValue(8.75);
                }
            }
            sheet.createRow(sheet.getLastRowNum() + 1).createCell(0).setCellValue("x bacon"); // mesmo nome, sem código
            sheet.getRow(sheet.getLastRowNum()).createCell(1).setCellValue("13,50");
            wb.write(out);
            preenchida = out.toByteArray();
        }
        var resp = itemService.importarPlanilha(loja.getId(), new org.springframework.mock.web.MockMultipartFile(
                "arquivo", "custos.xlsx", "application/octet-stream", preenchida));
        assertThat(resp.getItensAtualizados()).isEqualTo(5); // as 4 linhas com custo (pelo código) + "x bacon" pelo nome
        assertThat(resp.getItensImportados()).isZero();
        assertThat(itens.findByLojaIdAndAtivoTrue(loja.getId())).hasSize(antes);
        var combo = itens.findByLojaIdAndAtivoTrue(loja.getId()).stream()
                .filter(i -> i.getNomeItem().equals("Combo do dia")).findFirst().orElseThrow();
        assertThat(combo.getCmv()).isEqualByComparingTo("8.75");
        assertThat(itens.findById(xBacon.getId()).orElseThrow().getCmv()).isEqualByComparingTo("13.50");
    }

    static EntidadeIfood entidade(List<EntidadeIfood> l, String nome) {
        return l.stream().filter(e -> e.nome().equals(nome)).findFirst().orElseThrow();
    }

    static Item item(com.precificacao.precificacao.cardapio.CardapioModelos.Cardapio c, String id) {
        return c.catalogos().stream().flatMap(k -> k.categorias().stream()).flatMap(k -> k.itens().stream())
                .filter(i -> i.id().equals(id)).findFirst().orElseThrow();
    }
}
