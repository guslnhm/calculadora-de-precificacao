package com.precificacao.precificacao.painel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.painel.LeitorKpis.Dia;
import com.precificacao.precificacao.painel.Perdas.ChaveHora;
import com.precificacao.precificacao.painel.PainelRepository.Intervalo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PainelCalculosTest {

    static final ObjectMapper M = new ObjectMapper();
    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    static JsonNode json(String s) throws Exception {
        return M.readTree(s.replace('\'', '"'));
    }

    @Test
    void consultaDiasTemUmIntervaloPorDiaEFiltroDeData() {
        JsonNode c = LeitorKpis.consultaDias(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-03"));
        assertThat(c.path("agg").path("dateIntervals")).hasSize(3);
        assertThat(c.path("agg").path("dateIntervals").get(2).path("to").asText()).isEqualTo("2026-11-04");
        assertThat(c.path("filter").path("referenceDate").path("lte").asText()).isEqualTo("2026-11-03 23:59:59");
        assertThat(c.path("agg").path("groupBy").path("fields").get(0).asText()).isEqualTo("orderStatus");
    }

    @Test
    void leDiasSeparandoCanceladosEPonderandoTempos() throws Exception {
        JsonNode r = json("""
                {'data':[
                 {'dateRangeKey':{'value':{'from':'2026-11-01 00:00:00','to':'2026-11-02 00:00:00'},'count':12},
                  'groupByKey':{'value':{'orderStatus':'CONCLUDED'},'count':10},
                  'gmv':{'sum':500.0},'benefitByTypePartnerTotalValue':{'sum':20},'netDeliveryFee':{'sum':70},'timeToDeliveryInMin':{'avg':30}},
                 {'dateRangeKey':{'value':{'from':'2026-11-01 00:00:00','to':'2026-11-02 00:00:00'},'count':12},
                  'groupByKey':{'value':{'orderStatus':'CONFIRMED'},'count':1},
                  'gmv':{'sum':40.0},'netDeliveryFee':{'sum':5.5},'timeToDeliveryInMin':{'avg':60}},
                 {'dateRangeKey':{'value':{'from':'2026-11-01 00:00:00','to':'2026-11-02 00:00:00'},'count':12},
                  'groupByKey':{'value':{'orderStatus':'CANCELLED'},'count':1},'gmv':{'sum':55.5},'netDeliveryFee':{'sum':9}},
                 {'dateRangeKey':{'value':{'from':'2026-11-02 00:00:00','to':'2026-11-03 00:00:00'},'count':3},
                  'groupByKey':{'value':{'orderStatus':'CONCLUDED'},'count':3},'gmv':{'sum':120}}
                ]}""");
        Map<LocalDate, Dia> dias = LeitorKpis.lerDias(r, null);
        Dia d1 = dias.get(LocalDate.parse("2026-11-01"));
        assertThat(d1.pedidos).isEqualTo(11);
        assertThat(d1.cancelados).isEqualTo(1);
        assertThat(d1.gmv).isEqualByComparingTo("540");
        assertThat(d1.gmvCancelado).isEqualByComparingTo("55.5");
        assertThat(d1.incentivoLoja).isEqualByComparingTo("20");
        assertThat(d1.taxaEntrega).isEqualByComparingTo("75.5"); // frete só dos não cancelados
        assertThat(dias.get(LocalDate.parse("2026-11-02")).taxaEntrega).isNull(); // o iFood não mandou a métrica
        assertThat(d1.tempoEntrega).isEqualByComparingTo("32.73"); // (10*30 + 1*60) / 11
        assertThat(dias.get(LocalDate.parse("2026-11-02")).pedidos).isEqualTo(3);
        assertThat(LeitorKpis.temDatas(r)).isTrue();
    }

    @Test
    void semDateRangeKeyUsaODiaUnico() throws Exception {
        JsonNode r = json("{'data':[{'groupByKey':{'value':{'orderStatus':'CONCLUDED'},'count':4},'gmv':{'sum':100}}]}");
        assertThat(LeitorKpis.temDatas(r)).isFalse();
        assertThat(LeitorKpis.lerDias(r, LocalDate.parse("2026-11-05")).get(LocalDate.parse("2026-11-05")).pedidos)
                .isEqualTo(4);
    }

    @Test
    void novosClientesECurva() throws Exception {
        JsonNode n = json("""
                {'data':[
                 {'dateRangeKey':{'value':{'from':'2026-11-01 00:00:00'}},'groupByKey':{'value':{'customerFirstOrderInMerchant':true},'count':5}},
                 {'dateRangeKey':{'value':{'from':'2026-11-01 00:00:00'}},'groupByKey':{'value':{'customerFirstOrderInMerchant':false},'count':9}}
                ]}""");
        assertThat(LeitorKpis.lerNovosClientes(n, null)).containsEntry(LocalDate.parse("2026-11-01"), 5);

        JsonNode c = json("""
                {'data':[
                 {'groupByKey':{'value':{'dayOfWeek':'FRIDAY','orderCreationHour':19},'count':16},'gmv':{'sum':960}},
                 {'groupByKey':{'value':{'dayOfWeek':6,'orderCreationHour':20},'count':8},'gmv':{'sum':480}}
                ]}""");
        List<LeitorKpis.Hora> h = LeitorKpis.lerCurva(c);
        assertThat(h).extracting(LeitorKpis.Hora::diaSemana).containsExactly(5, 6);
        assertThat(h.get(0).gmv()).isEqualByComparingTo("960");
    }

    @Test
    void perdaProporcionalAosMinutosEIgnoraHorasSemVenda() {
        // sexta: 19h vende R$ 120/h, 20h R$ 60/h; 21h não vende
        Map<ChaveHora, BigDecimal> curva = Map.of(new ChaveHora(5, 19), new BigDecimal("120"),
                new ChaveHora(5, 20), new BigDecimal("60"));
        OffsetDateTime ini = OffsetDateTime.parse("2026-11-13T19:30:00-03:00"); // sexta
        OffsetDateTime fim = OffsetDateTime.parse("2026-11-13T21:45:00-03:00");
        Perdas.Estimativa e = Perdas.estimar(curva, ini, fim, SP);
        assertThat(e.minutos()).isEqualTo(90);               // 30 min às 19h + 60 min às 20h
        assertThat(e.valor()).isEqualByComparingTo("120");   // 60 + 60
    }

    @Test
    void unirJuntaIncidentesSobrepostosDaMesmaLoja() {
        OffsetDateTime t = OffsetDateTime.parse("2026-11-13T18:00:00-03:00");
        List<Intervalo> u = PainelService.unir(List.of(
                new Intervalo("a", t, t.plusHours(2)),
                new Intervalo("a", t.plusHours(1), t.plusHours(3)),
                new Intervalo("b", t, t.plusHours(1))));
        assertThat(u).hasSize(2);
        assertThat(u.get(0).fim()).isEqualTo(t.plusHours(3));
    }

    @Test
    void periodosSemanaSegundaADomingoEMes() {
        PainelService.Periodo s = PainelService.semana(LocalDate.parse("2026-10-22"));
        assertThat(s.inicio()).isEqualTo(LocalDate.parse("2026-10-19"));
        assertThat(s.fim()).isEqualTo(LocalDate.parse("2026-10-25"));
        assertThat(s.anterior().inicio()).isEqualTo(LocalDate.parse("2026-10-12"));
        PainelService.Periodo m = PainelService.mes(LocalDate.parse("2026-02-10"));
        assertThat(m.fim()).isEqualTo(LocalDate.parse("2026-02-28"));
        assertThat(PainelService.pct(new BigDecimal("92.2"), new BigDecimal("100"))).isEqualByComparingTo("-7.8");
    }

    @Test
    void urlDoRailwayViraJdbcNoBancoPedidos() {
        BiImportService.Jdbc j = BiImportService.jdbc(
                "postgresql://postgres:s%40nha@trolley.proxy.rlwy.net:41234/railway", "pedidos");
        assertThat(j.url()).isEqualTo("jdbc:postgresql://trolley.proxy.rlwy.net:41234/pedidos");
        assertThat(j.usuario()).isEqualTo("postgres");
        assertThat(j.senha()).isEqualTo("s@nha");
        assertThat(BiImportService.plataforma("iFood")).isEqualTo("IFOOD");
        assertThat(BiImportService.plataforma("99Food")).isEqualTo("99FOOD");
    }

    @Test
    void vinculoPorNomeSoComNomeBemParecido() {
        Map<String, String> lojas = Map.of("m1", "Chicken Lovers - Jk Shopping", "m2", "Qg - Jk Shopping");
        assertThat(BiImportService.porNome(lojas, "Chicken Lovers Jk Shopping", null)).isEqualTo("m1");
        assertThat(BiImportService.porNome(lojas, "Pizzaria Qualquer", null)).isNull();
    }

    @Test
    void vinculoPeloAliasDoIfood() {
        Map<String, String> lojas = Map.of("u1", "Açaí Puríssimo - Águas Claras Norte", "u2", "Açaí Puríssimo - Águas Claras Norte",
                "u3", "Qg - Asa Norte", "u4", "Bravo Hamburgueria");
        // nome igual em duas lojas: o short id decide
        List<PainelRepository.Alias> dup = List.of(new PainelRepository.Alias(7, "2278457", "Açaí Puríssimo - Águas Claras Norte"));
        assertThat(BiImportService.escolher(dup, lojas, Map.of("2278457", "u2"))).containsExactly("u2", "ID");
        assertThat(BiImportService.escolher(dup, lojas, Map.of())[0]).isNull();
        // nome do iFood igual (ignorando acento/espaço)
        List<PainelRepository.Alias> qg = List.of(new PainelRepository.Alias(30, "1325742", "QG -  Asa Norte"));
        assertThat(BiImportService.escolher(qg, lojas, Map.of())).containsExactly("u3", "NOME_IFOOD");
        // nome genérico da operação não liga a loja errada
        List<PainelRepository.Alias> nada = List.of(new PainelRepository.Alias(40, "999", "Asa Sul"));
        assertThat(BiImportService.escolher(nada, lojas, Map.of())).containsExactly(null, "NENHUM");
    }

    @Test
    void resumoDoDiaAnteriorSoComOQueFoiRelevante() {
        LocalDate d = LocalDate.parse("2026-11-16");
        // 30 dias de histórico: até 140 pedidos / R$ 9.400 por dia, cerca de 1 cancelamento por dia
        List<PainelRepository.LinhaDia> linhas = new java.util.ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            for (String m : List.of("a", "b", "c")) {
                int ped = i == 14 ? 140 : 100;
                BigDecimal fat = new BigDecimal(i == 14 ? "9400.00" : "6000.00");
                linhas.add(new PainelRepository.LinhaDia(m, d.minusDays(i), ped, 1, fat, null, null, null, null, null, null));
            }
        }
        var hist = ResumoDiarioService.historicos(linhas, d);
        assertThat(hist.get("a").diasComVenda()).isEqualTo(30);
        assertThat(hist.get("a").mediaCancelados()).isEqualTo(1.0);

        // a: recorde de faturamento e fechada 1h20; b: 15 cancelamentos; c: dia normal (fechada só 20 min)
        var a = ResumoDiarioService.comDestaques(new ResumoDiarioService.Loja("Vo no Box - Asa Norte", 152,
                new BigDecimal("9870.00"), 3, 2, 80, new BigDecimal("640.40")), hist.get("a"));
        var b = ResumoDiarioService.comDestaques(new ResumoDiarioService.Loja("Qg - Asa Norte", 90,
                new BigDecimal("5000.00"), 15, 0, 0, BigDecimal.ZERO), hist.get("b"));
        var c = ResumoDiarioService.comDestaques(new ResumoDiarioService.Loja("Calmo", 100,
                new BigDecimal("6000.00"), 1, 1, 20, BigDecimal.ZERO), hist.get("c"));
        assertThat(c.relevante()).isFalse();

        String t = ResumoDiarioService.mensagem(d, List.of(c, b, a));
        assertThat(t).startsWith("📊 *Resumo iFood — segunda, 16/11*\n\n*Vo no Box - Asa Norte*\n"
                + "🏆 *Recorde de faturamento!* (antes R$ 9.400, em 02/11)\n"
                + "🏆 *Recorde de pedidos!* (antes 140, em 02/11)\n"
                + "⏰ *Loja ficou fechada 1h20* no horário de funcionamento\n"
                + "🧾 152 pedidos · R$ 9.870 · ticket R$ 64,93");
        assertThat(t).contains("*Qg - Asa Norte*\n🚨 *Cancelamentos acima do normal:* 15 (a loja costuma ter 1 por dia)");
        assertThat(t).doesNotContain("Calmo");
        assertThat(t).endsWith("_A outra loja do grupo teve um dia sem nada fora do normal._");

        // só dia normal: não sai mensagem
        assertThat(ResumoDiarioService.mensagem(d, List.of(c))).isNull();
        // sem histórico suficiente não tem recorde
        var nova = ResumoDiarioService.comDestaques(new ResumoDiarioService.Loja("Nova", 50, new BigDecimal("3000"), 0, 0, 0,
                BigDecimal.ZERO), new ResumoDiarioService.Historico(10, 40, d.minusDays(3), new BigDecimal("2000"), d.minusDays(3), 0));
        assertThat(nova.relevante()).isFalse();
    }

    static PainelRepository.PeriodoBi bi(String bruto, String liquido, String recebido, String repassado, String repasse) {
        return new PainelRepository.PeriodoBi("m1", "IFOOD", "SEMANAL", LocalDate.parse("2026-11-16"),
                LocalDate.parse("2026-11-22"), 10, new BigDecimal("1000"), null, repasse == null ? null : new BigDecimal(repasse),
                new BigDecimal("0.2"), null, null, 0, null, null, null,
                bruto == null ? null : new BigDecimal(bruto), liquido == null ? null : new BigDecimal(liquido),
                recebido == null ? null : new BigDecimal(recebido), repassado == null ? null : new BigDecimal(repassado));
    }

    @Test
    void taxaDeRepasseECustoDaPlataformaPelosValoresDoPortal() {
        // repasse = 140 + 10 = 150; taxa = 150 / (200 - 5) = 0,7692; custo = 0,2308
        PainelService.Portal manual = PainelService.Portal.de(new PainelRepository.Manual("m1", "SEMANAL",
                LocalDate.parse("2026-11-16"), new BigDecimal("0.19"), null, null, null, new BigDecimal("200"),
                new BigDecimal("140"), new BigDecimal("10"), new BigDecimal("5")), bi("999", "1", null, null, "1"));
        assertThat(manual.repasse()).isEqualByComparingTo("150");
        assertThat(manual.taxa()).isEqualByComparingTo("0.7692");
        assertThat(manual.custo()).isEqualByComparingTo("0.2308");
        assertThat(manual.conversao()).isEqualByComparingTo("0.19");
        assertThat(manual.repasseManual()).isTrue();

        // sem lançamento: valores da base; recebido e repassado em branco contam como 0
        PainelService.Portal base = PainelService.Portal.de(null, bi("300", "210", null, null, null));
        assertThat(base.repasse()).isEqualByComparingTo("210");
        assertThat(base.taxa()).isEqualByComparingTo("0.7");
        assertThat(base.custo()).isEqualByComparingTo("0.3");
        assertThat(base.conversao()).isEqualByComparingTo("0.2");
        assertThat(base.repasseManual()).isFalse();

        // lançamento antigo (só repasse em R$): usa o bruto da base para a taxa
        PainelService.Portal antigo = PainelService.Portal.de(new PainelRepository.Manual("m1", "SEMANAL",
                LocalDate.parse("2026-11-16"), null, new BigDecimal("160"), null, null), bi("200", null, null, null, null));
        assertThat(antigo.taxa()).isEqualByComparingTo("0.8");

        // sem bruto não há taxa
        assertThat(PainelService.Portal.de(null, bi(null, "100", null, null, null)).taxa()).isNull();
        assertThat(PainelService.Portal.de(null, null).repasse()).isNull();
    }
}
