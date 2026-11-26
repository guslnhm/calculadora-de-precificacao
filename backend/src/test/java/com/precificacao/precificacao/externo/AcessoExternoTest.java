package com.precificacao.precificacao.externo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Acesso de fora: chave própria, só as lojas liberadas, JSON e CSV, desativar e trocar a chave. */
@SpringBootTest
@AutoConfigureMockMvc
public class AcessoExternoTest {

    static final String A = "ext-loja-a";
    static final String B = "ext-loja-b";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AcessoExternoRepository repo;
    @Autowired ObjectMapper json;

    String admin;

    @BeforeEach
    void preparar() throws Exception {
        repo.limparTudo();
        for (String t : List.of("senha_link", "usuario_login", "usuario_atividade", "sessao", "usuario_loja", "usuarios", "convite")) {
            jdbc.update("DELETE FROM " + t);
        }
        jdbc.update("DELETE FROM painel_dia WHERE merchant_id LIKE 'ext-%'");
        jdbc.update("DELETE FROM painel_short_id WHERE merchant_id LIKE 'ext-%'");
        jdbc.update("DELETE FROM ifood_merchant WHERE merchant_id LIKE 'ext-%'");
        jdbc.update("INSERT INTO ifood_merchant (merchant_id, nome, ativo) VALUES (?, 'Loja \"A\"', TRUE), (?, 'Loja B', TRUE)", A, B);
        for (String m : List.of(A, B)) {
            jdbc.update("""
                    INSERT INTO painel_dia (merchant_id, dia, pedidos, cancelados, gmv, gmv_cancelado, novos_clientes, coletado_em,
                        taxa_entrega, incentivo_loja)
                    VALUES (?, '2026-11-24', 40, 2, 2500.50, 120.00, 7, now(), 300.00, 150.25),
                           (?, '2026-11-25', 0, 0, 0, 0, NULL, now(), NULL, NULL)""", m, m);
        }
        jdbc.update("INSERT INTO painel_short_id (merchant_id, short_id, atualizado_em) VALUES (?, '1109497', now())", A);
        jdbc.update("""
                INSERT INTO usuarios (nome, email, senha_hash, perfil, ativo, criado_em, atualizado_em)
                VALUES ('Admin', 'admin', 'admin', 'ADMIN', TRUE, now(), now())""");
        admin = chamar(post("/auth/login"), null, Map.of("email", "admin", "senha", "admin"), 200).get("token").asText();
    }

    JsonNode chamar(MockHttpServletRequestBuilder req, String token, Object corpo, int esperado) throws Exception {
        if (token != null) {
            req.header("Authorization", token);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        }
        String body = mvc.perform(req).andExpect(status().is(esperado)).andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    @Test
    void chaveSoVeAsLojasLiberadas() throws Exception {
        JsonNode criado = chamar(post("/acessos/externo"), admin, Map.of("nome", "BI de metas", "lojas", List.of(A)), 200);
        String chave = criado.get("chave").asText();
        assertThat(chave).startsWith("rzq_");
        assertThat(criado.get("acesso").get("inicioChave").asText()).endsWith("…");
        // a chave inteira não volta na listagem
        assertThat(chamar(get("/acessos/externo"), admin, null, 200).toString()).doesNotContain(chave);

        // sem chave, chave errada, ou o login do site: não entra
        chamar(get("/externo/vendas"), null, null, 401);
        chamar(get("/externo/vendas"), "Bearer rzq_errada", null, 401);
        chamar(get("/externo/vendas"), admin, null, 401);

        JsonNode v = chamar(get("/externo/vendas?de=2026-11-24&ate=2026-11-25"), "Bearer " + chave, null, 200).get("vendas");
        assertThat(v).hasSize(2);
        assertThat(v.get(0).get("merchantId").asText()).isEqualTo(A);
        assertThat(v.get(0).get("faturamento").decimalValue()).isEqualByComparingTo("2500.50");
        assertThat(v.get(0).get("ticketMedio").decimalValue()).isEqualByComparingTo("62.51");
        assertThat(v.get(1).get("ticketMedio").isNull()).isTrue();
        // itens sem frete e antes dos descontos da loja: 2500,50 - 300,00 + 150,25
        assertThat(v.get(0).get("valorItens").decimalValue()).isEqualByComparingTo("2350.75");
        assertThat(v.get(0).get("idLojaPortal").asText()).isEqualTo("1109497");
        assertThat(v.get(1).get("valorItens").isNull()).isTrue(); // dia sem frete coletado

        // pedir a loja B com a chave da A: proibido
        chamar(get("/externo/vendas?loja=" + B), "Bearer " + chave, null, 403);
        // lojas da chave
        JsonNode l = chamar(get("/externo/lojas").header("X-Chave", chave), null, null, 200);
        assertThat(l.get("lojas")).hasSize(1);

        // mentorado/equipe não precisa de nada disso: o resto do site continua exigindo login
        chamar(get("/acessos/externo"), "Bearer " + chave, null, 401);
    }

    @Test
    void csvPorLinkComChaveNaUrl() throws Exception {
        String chave = chamar(post("/acessos/externo"), admin, Map.of("nome", "Looker", "lojas", List.of(A, B)), 200)
                .get("chave").asText();
        String csv = mvc.perform(get("/externo/vendas?de=2026-11-24&ate=2026-11-24&formato=csv&chave=" + chave))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] linhas = csv.split("\r\n");
        assertThat(linhas[0]).isEqualTo("data,merchant_id,loja,pedidos,cancelados,faturamento,faturamento_cancelado,"
                + "ticket_medio,novos_clientes,atualizado_em,id_loja_portal,valor_itens,taxa_entrega,desconto_loja");
        assertThat(linhas).hasSize(3);
        assertThat(linhas[1]).startsWith("2026-11-24,ext-loja-a,\"Loja \"\"A\"\"\",40,2,2500.50,120.00,62.51,7,");
        assertThat(linhas[1]).endsWith(",1109497,2350.75,300.00,150.25");
        assertThat(linhas[2]).endsWith(",,2350.75,300.00,150.25"); // loja B sem id curto

        String excel = mvc.perform(get("/externo/vendas?de=2026-11-24&ate=2026-11-24&formato=csv-excel&loja=" + B)
                        .header("X-Chave", chave))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(excel).startsWith("﻿data;merchant_id;loja;");
        assertThat(excel).contains("2026-11-24;ext-loja-b;\"Loja B\";40;2;2500,50;120,00;62,51;7;");
        assertThat(excel).contains(";;2350,75;300,00;150,25");
    }

    @Test
    void desativarETrocarAChave() throws Exception {
        JsonNode criado = chamar(post("/acessos/externo"), admin, Map.of("nome", "BI", "lojas", List.of(A)), 200);
        long id = criado.get("acesso").get("id").asLong();
        String chave = criado.get("chave").asText();
        chamar(get("/externo/lojas"), "Bearer " + chave, null, 200);

        chamar(put("/acessos/externo/" + id), admin, Map.of("ativo", false), 200);
        chamar(get("/externo/lojas"), "Bearer " + chave, null, 401);
        chamar(put("/acessos/externo/" + id), admin, Map.of("ativo", true, "lojas", List.of(A, B)), 200);

        String nova = chamar(post("/acessos/externo/" + id + "/nova-chave"), admin, null, 200).get("chave").asText();
        chamar(get("/externo/lojas"), "Bearer " + chave, null, 401);       // a antiga parou
        assertThat(chamar(get("/externo/lojas"), "Bearer " + nova, null, 200).get("lojas")).hasSize(2);

        JsonNode lista = chamar(get("/acessos/externo"), admin, null, 200).get("acessos");
        assertThat(lista.get(0).get("usos").asLong()).isGreaterThanOrEqualTo(2);

        // validações
        chamar(post("/acessos/externo"), admin, Map.of("nome", "", "lojas", List.of(A)), 400);
        chamar(post("/acessos/externo"), admin, Map.of("nome", "X", "lojas", List.of()), 400);
        chamar(post("/acessos/externo"), admin, Map.of("nome", "X", "lojas", List.of("nao-existe")), 400);
        chamar(get("/externo/vendas?de=2026-11-25&ate=2026-11-01"), "Bearer " + nova, null, 400);
    }
}
