package com.precificacao.precificacao.food99;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.cardapio.CardapioTest.Relogio;
import com.precificacao.precificacao.ifood.IfoodProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Financeiro da 99Food: assinatura, token, lojas vinculadas, extrato paginado e soma por dia. */
@SpringBootTest
public class Food99Test {

    @Autowired Food99Repository repo;
    final ObjectMapper json = Food99Http.JSON;

    /** Chamadas recebidas pela 99 falsa. */
    final List<String> chamadas = new ArrayList<>();
    int tokensEmitidos;
    Relogio relogio;
    Food99Service service;

    @BeforeEach
    void preparar() {
        repo.limparTudo();
        relogio = new Relogio(Instant.parse("2026-11-26T15:00:00Z"));
        Food99Properties props = new Food99Properties("3458764573578035295", "segredo", "http://x", true, 3, 100);
        Food99Api falsa = (caminho, corpo, bearer) -> responder(caminho, corpo, bearer);
        service = new Food99Service(new Food99Client(props, falsa, relogio), repo, props, relogio,
                new IfoodProperties("", "", "http://x", "America/Sao_Paulo", 5, 4));
    }

    JsonNode ler(String s) {
        try {
            return json.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    JsonNode responder(String caminho, Map<String, Object> corpo, String bearer) {
        chamadas.add(caminho + (bearer == null ? "" : " [" + bearer + "]"));
        return switch (caminho) {
            case Food99Client.TOKEN -> {
                assertThat(corpo).containsEntry("retailer", "3458764573578035295").containsEntry("secret", "segredo");
                tokensEmitidos++;
                yield ler("{\"accessToken\":\"tok" + tokensEmitidos + "\",\"expiresIn\":21600}");
            }
            case Food99Client.LOJAS -> {
                assertThat(corpo.get("sign")).isEqualTo(Food99Client.assinar(corpo, "segredo"));
                yield ler("""
                        {"errno":0,"errmsg":"ok","data":{"page_no":1,"total_page":1,"shops":[
                          {"shop_id":5764608924570091908,"app_shop_id":"loja-1","shop_name":"Burger 99","bound_flag":1},
                          {"shop_id":5764608924570091999,"shop_name":"Sem vínculo","bound_flag":0}]}}""");
            }
            case Food99Client.EXTRATO -> {
                assertThat(corpo).containsEntry("acceptor_code", "loja-1");
                if ("tok1".equals(bearer)) {
                    yield ler("{\"errno\":10102,\"errmsg\":\"token expired\"}"); // força refazer o token
                }
                int pagina = (Integer) corpo.get("page_no");
                if (!corpo.get("start_date").equals("20261123")) {
                    yield ler("{\"errno\":0,\"data\":{\"data\":[],\"total_page\":1}}");
                }
                // 25/11 12:00 (SP): pedido A (vale), pedido B (cancelado depois) e uma despesa de anúncio
                long ts = 1795618800L;
                String a = item("1001", 1, ts, 5000, -500, 200, -600, -100, 3800);
                String b = item("1002", 1, ts, 3000, 0, 0, -360, -60, 2580);
                String bEstorno = item("1002", 2, ts + 60, 0, 0, 0, 0, 0, -2580);
                String anuncio = item("ad-7", 5, ts, 0, 0, 0, 0, 0, -1000);
                yield ler(pagina == 1
                        ? "{\"errno\":0,\"data\":{\"data\":[" + a + "," + b + "],\"total_page\":2}}"
                        : "{\"errno\":0,\"data\":{\"data\":[" + bEstorno + "," + anuncio + "],\"total_page\":2}}");
            }
            case Food99Client.LINK_AUTORIZACAO ->
                    ler("{\"errno\":0,\"errmsg\":\"OK\",\"data\":{\"url\":\"https://merchant.99app.com/pt-BR/manager/app-authorize?x=1\"}}");
            default -> throw new IllegalStateException(caminho);
        };
    }

    static String item(String pedido, int tipo, long ts, long itens, long desconto, long subsidio, long comissao,
                       long taxaPgto, long liquido) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shopId", 5764608924570091908L);
        m.put("orderId", pedido);
        m.put("orderType", tipo);
        m.put("deliveryType", 1);
        m.put("businessTs", ts);
        m.put("mealOriginalAmount", itens);
        m.put("shopActivityOutcome", desconto);
        m.put("shopActivitySubsidy", subsidio);
        m.put("commissionAmount", comissao);
        m.put("payCommissionAmount", taxaPgto);
        m.put("settlementAmount", liquido);
        m.put("paymentChannel", 212);
        try {
            return Food99Http.JSON.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void assinaturaComoNoExemploDaDocumentacao() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("timestamp", 1623814795L);
        p.put("app_id", new java.math.BigInteger("3458764573578035295"));
        p.put("page_size", 30);
        p.put("page_no", 1);
        assertThat(Food99Client.assinar(p, "87b69bab1a1548c1516189a5fb75e705")).isEqualTo("d471c1f850ab312a703ac9611bdee25a");
    }

    @Test
    void coletaLojasVinculadasESomaPorDia() {
        Food99Service.Resultado r = service.coletarRecentes(); // 23/11 a 25/11
        assertThat(r.erros()).isEmpty();
        assertThat(r.lojas()).isEqualTo(1);
        assertThat(r.lancamentos()).isEqualTo(4);
        assertThat(tokensEmitidos).isEqualTo(2); // o 1º foi recusado e refeito
        assertThat(chamadas).noneMatch(c -> c.contains("Sem vínculo"));

        List<Food99Service.DiaReais> dias = service.dias(LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-30"));
        assertThat(dias).hasSize(1);
        Food99Service.DiaReais d = dias.get(0);
        assertThat(d.dia()).isEqualTo(LocalDate.parse("2026-11-25"));
        assertThat(d.loja()).isEqualTo("Burger 99");
        assertThat(d.shopId()).isEqualTo("5764608924570091908"); // id de 64 bits sem arredondar
        assertThat(d.pedidos()).isEqualTo(1);
        assertThat(d.cancelados()).isEqualTo(1);
        assertThat(d.valorItens()).isEqualByComparingTo("50.00");
        assertThat(d.descontoLoja()).isEqualByComparingTo("-5.00");
        assertThat(d.subsidio99()).isEqualByComparingTo("2.00");
        assertThat(d.comissao()).isEqualByComparingTo("-6.00");
        assertThat(d.taxaPagamento()).isEqualByComparingTo("-1.00");
        assertThat(d.outrosLancamentos()).isEqualByComparingTo("-10.00");
        // 38,00 (A) + 25,80 (B) - 25,80 (estorno de B) - 10,00 (anúncio)
        assertThat(d.liquido()).isEqualByComparingTo("28.00");

        // coletar de novo não duplica
        service.coletarRecentes();
        assertThat(service.dias(LocalDate.parse("2026-11-25"), LocalDate.parse("2026-11-25")).get(0).liquido())
                .isEqualByComparingTo("28.00");
        assertThat((List<?>) service.situacao().get("lojas")).hasSize(2);
    }

    @Test
    void linkDeAutorizacao() {
        assertThat(service.linkAutorizacao()).startsWith("https://merchant.99app.com/");
    }
}
