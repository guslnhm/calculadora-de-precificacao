package com.precificacao.precificacao.acesso;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, convite, permissões do mentorado e gestão de acessos, de ponta a ponta pela API. */
@SpringBootTest
@AutoConfigureMockMvc
public class AcessoTest {

    static final String LOJA_A = "acs-loja-a";
    static final String LOJA_B = "acs-loja-b";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AcessoRepository repo;
    @Autowired ObjectMapper json;

    @BeforeEach
    void preparar() {
        repo.limparTudo();
        jdbc.update("DELETE FROM ifood_merchant WHERE merchant_id LIKE 'acs-%'");
        jdbc.update("INSERT INTO ifood_merchant (merchant_id, nome, ativo) VALUES (?, 'Loja A', TRUE), (?, 'Loja B', TRUE)",
                LOJA_A, LOJA_B);
        // usuário antigo, com senha em texto puro (como estava antes)
        jdbc.update("""
                INSERT INTO usuarios (nome, email, senha_hash, perfil, ativo, criado_em, atualizado_em)
                VALUES ('Admin', 'admin', 'admin', 'ADMIN', TRUE, now(), now())""");
    }

    // ----------------------------------------------------------------- apoio

    JsonNode chamar(MockHttpServletRequestBuilder req, String token, Object corpo, int esperado) throws Exception {
        if (token != null) {
            req.header("Authorization", token);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(corpo));
        }
        ResultActions r = mvc.perform(req).andExpect(status().is(esperado));
        String body = r.andReturn().getResponse().getContentAsString();
        return body.isBlank() ? null : json.readTree(body);
    }

    String login(String email, String senha) throws Exception {
        return chamar(post("/auth/login"), null, Map.of("email", email, "senha", senha), 200).get("token").asText();
    }

    String criarConvite(String admin, Object... lojas) throws Exception {
        JsonNode c = chamar(post("/acessos/convites"), admin,
                Map.of("descricao", "Teste", "lojas", lojas, "validadeDias", 7, "maxUsos", 1), 200);
        assertThat(c.get("situacao").asText()).isEqualTo("ATIVO");
        return c.get("token").asText();
    }

    Map<String, Object> cadastro(String email) {
        return Map.of("nome", "Maria", "email", email, "senha", "senha-forte-1",
                "dataNascimento", LocalDate.of(1990, 5, 20).toString());
    }

    // ----------------------------------------------------------------- testes

    @Test
    void senhaAntigaViraBcryptEPedeTroca() throws Exception {
        String token = login("ADMIN", "admin");
        assertThat(jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE email = 'admin'", String.class))
                .startsWith("$2");
        JsonNode eu = chamar(get("/auth/eu"), token, null, 200);
        assertThat(eu.get("trocarSenha").asBoolean()).isTrue();
        assertThat(eu.get("mentorado").asBoolean()).isFalse();

        // continua entrando com a mesma senha, agora pelo BCrypt
        login("admin", "admin");
        chamar(put("/auth/senha"), token, Map.of("atual", "admin", "nova", "curta"), 400);
        chamar(put("/auth/senha"), token, Map.of("atual", "admin", "nova", "nova-senha-123"), 200);
        assertThat(chamar(get("/auth/eu"), token, null, 200).get("trocarSenha").asBoolean()).isFalse();
        chamar(post("/auth/login"), null, Map.of("email", "admin", "senha", "admin"), 401);
        login("admin", "nova-senha-123");
    }

    @Test
    void semTokenOuTokenInvalidoDa401() throws Exception {
        chamar(get("/painel/carteira"), null, null, 401);
        chamar(get("/painel/carteira"), "token-inventado", null, 401);
        String token = login("admin", "admin");
        chamar(post("/auth/logout"), token, null, 200);
        chamar(get("/auth/eu"), token, null, 401);
    }

    @Test
    void bloqueiaDepoisDeMuitasSenhasErradas() throws Exception {
        // e-mail próprio deste teste: o bloqueio fica na memória e não pode atrapalhar os outros testes
        jdbc.update("""
                INSERT INTO usuarios (nome, email, senha_hash, perfil, ativo, criado_em, atualizado_em)
                VALUES ('Bloq', 'bloq@exemplo.com', 'senha-do-bloq', 'ADMIN', TRUE, now(), now())""");
        for (int i = 0; i < 5; i++) {
            chamar(post("/auth/login"), null, Map.of("email", "bloq@exemplo.com", "senha", "errada"), 401);
        }
        chamar(post("/auth/login"), null, Map.of("email", "bloq@exemplo.com", "senha", "senha-do-bloq"), 429);
        login("admin", "admin");
    }

    @Test
    void conviteCadastraMentoradoQueSoVeAsProprias() throws Exception {
        String admin = login("admin", "admin");
        String convite = criarConvite(admin, LOJA_A);

        JsonNode pub = chamar(get("/convites/" + convite), null, null, 200);
        assertThat(pub.get("valido").asBoolean()).isTrue();
        assertThat(pub.get("lojas").get(0).asText()).isEqualTo("Loja A");

        // sem data de nascimento não cadastra
        Map<String, Object> semData = new java.util.HashMap<>(cadastro("maria@exemplo.com"));
        semData.remove("dataNascimento");
        chamar(post("/convites/" + convite + "/aceitar"), null, semData, 400);

        JsonNode r = chamar(post("/convites/" + convite + "/aceitar"), null, cadastro("Maria@Exemplo.com"), 200);
        String m = r.get("token").asText();
        assertThat(r.get("perfil").asText()).isEqualTo("MENTORADO");

        // convite de 1 uso já se esgotou
        assertThat(chamar(get("/convites/" + convite), null, null, 200).get("valido").asBoolean()).isFalse();
        chamar(post("/convites/" + convite + "/aceitar"), null, cadastro("outra@exemplo.com"), 410);

        // painel: só a loja A
        JsonNode cart = chamar(get("/painel/carteira"), m, null, 200);
        assertThat(cart.get("linhas")).hasSize(1);
        assertThat(cart.get("linhas").get(0).get("chave").asText()).isEqualTo(LOJA_A);
        chamar(get("/painel/loja").param("chave", LOJA_A), m, null, 200);
        chamar(get("/painel/loja").param("chave", LOJA_B), m, null, 403);

        // nada além do painel de leitura
        chamar(put("/painel/manual"), m, Map.of("chave", LOJA_A), 403);
        chamar(get("/painel/status"), m, null, 403);
        chamar(get("/lojas"), m, null, 403);
        chamar(get("/monitor/lojas"), m, null, 403);
        chamar(get("/acessos/usuarios"), m, null, 403);
        chamar(post("/acessos/convites"), m, Map.of("lojas", new String[]{LOJA_B}), 403);

        JsonNode eu = chamar(get("/auth/eu"), m, null, 200);
        assertThat(eu.get("mentorado").asBoolean()).isTrue();
        assertThat(eu.get("lojas").get(0).asText()).isEqualTo(LOJA_A);
        login("maria@exemplo.com", "senha-forte-1");

        // a equipe vê o uso e a data de nascimento
        JsonNode us = chamar(get("/acessos/usuarios"), admin, null, 200);
        JsonNode maria = null;
        for (JsonNode u : us) {
            if ("maria@exemplo.com".equals(u.get("email").asText())) {
                maria = u;
            }
        }
        assertThat(maria).isNotNull();
        assertThat(maria.get("dataNascimento").asText()).isEqualTo("1990-05-20");
        assertThat(maria.get("ultimoAcesso").isNull()).isFalse();
        assertThat(maria.get("diasAtivos7").asInt()).isEqualTo(1);
        assertThat(maria.get("logins30").asInt()).isEqualTo(2);
        assertThat(chamar(get("/acessos/adesao"), admin, null, 200).get("ativos7").asInt()).isEqualTo(1);

        // ganha a loja B pela tela de acessos
        long id = maria.get("id").asLong();
        chamar(put("/acessos/usuarios/" + id), admin, Map.of("lojas", new String[]{LOJA_A, LOJA_B}), 200);
        chamar(get("/painel/loja").param("chave", LOJA_B), m, null, 200);
        assertThat(chamar(get("/painel/carteira"), m, null, 200).get("linhas")).hasSize(2);

        // desativado: a sessão cai
        chamar(put("/acessos/usuarios/" + id), admin, Map.of("ativo", false), 200);
        chamar(get("/painel/carteira"), m, null, 401);
        chamar(post("/auth/login"), null, Map.of("email", "maria@exemplo.com", "senha", "senha-forte-1"), 401);
    }

    @Test
    void emailQueJaTemAcessoJuntaAsLojas() throws Exception {
        String admin = login("admin", "admin");
        chamar(post("/convites/" + criarConvite(admin, LOJA_A) + "/aceitar"), null, cadastro("joao@exemplo.com"), 200);

        String outro = criarConvite(admin, LOJA_B);
        Map<String, Object> senhaErrada = new java.util.HashMap<>(cadastro("joao@exemplo.com"));
        senhaErrada.put("senha", "outra-senha-9");
        chamar(post("/convites/" + outro + "/aceitar"), null, senhaErrada, 409);

        String m = chamar(post("/convites/" + outro + "/aceitar"), null, cadastro("joao@exemplo.com"), 200)
                .get("token").asText();
        assertThat(chamar(get("/auth/eu"), m, null, 200).get("lojas")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE lower(email) = 'joao@exemplo.com'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void conviteRevogadoNaoServe() throws Exception {
        String admin = login("admin", "admin");
        String convite = criarConvite(admin, LOJA_A);
        long id = chamar(get("/acessos/convites"), admin, null, 200).get(0).get("id").asLong();
        chamar(delete("/acessos/convites/" + id), admin, null, 200);
        assertThat(chamar(get("/convites/" + convite), null, null, 200).get("valido").asBoolean()).isFalse();
        chamar(post("/convites/" + convite + "/aceitar"), null, cadastro("x@exemplo.com"), 410);
        chamar(get("/convites/nao-existe"), null, null, 200);
        chamar(post("/acessos/convites"), admin, Map.of("lojas", new String[]{"loja-que-nao-existe"}), 400);
        chamar(post("/acessos/convites"), admin, Map.of("lojas", new String[]{}), 400);
    }

    @Test
    void linkDeNovaSenha() throws Exception {
        String admin = login("admin", "admin");
        chamar(post("/convites/" + criarConvite(admin, LOJA_A) + "/aceitar"), null, cadastro("ana@exemplo.com"), 200);
        long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = 'ana@exemplo.com'", Long.class);

        String link = chamar(post("/acessos/usuarios/" + id + "/link-senha"), admin, null, 200).get("token").asText();
        assertThat(chamar(get("/senha/" + link), null, null, 200).get("valido").asBoolean()).isTrue();
        chamar(post("/senha/" + link), null, Map.of("senha", "123"), 400);
        chamar(post("/senha/" + link), null, Map.of("senha", "senha-nova-77"), 200);
        chamar(post("/senha/" + link), null, Map.of("senha", "senha-nova-88"), 410);
        login("ana@exemplo.com", "senha-nova-77");
    }

    @Test
    void naoDesativaASiMesmo() throws Exception {
        String admin = login("admin", "admin");
        long id = jdbc.queryForObject("SELECT id FROM usuarios WHERE email = 'admin'", Long.class);
        chamar(put("/acessos/usuarios/" + id), admin, Map.of("ativo", false), 400);
    }

    @Test
    void diasParaAniversario() {
        LocalDate hoje = LocalDate.of(2026, 11, 19);
        assertThat(AcessoService.diasParaAniversario(LocalDate.of(1990, 11, 19), hoje)).isZero();
        assertThat(AcessoService.diasParaAniversario(LocalDate.of(1990, 11, 20), hoje)).isEqualTo(1);
        assertThat(AcessoService.diasParaAniversario(LocalDate.of(1990, 11, 18), hoje)).isEqualTo(364);
        assertThat(AcessoService.diasParaAniversario(null, hoje)).isNull();
    }
}
