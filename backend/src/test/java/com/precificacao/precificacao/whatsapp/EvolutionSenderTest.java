package com.precificacao.precificacao.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvolutionSenderTest {

    record Pedido(String metodo, String caminho, String apikey, String corpo) {
    }

    HttpServer servidor;
    final Map<String, Object[]> rotas = new HashMap<>();
    final List<Pedido> pedidos = new ArrayList<>();
    EvolutionSender evo;

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String caminho = ex.getRequestURI().getRawPath()
                    + (ex.getRequestURI().getRawQuery() == null ? "" : "?" + ex.getRequestURI().getRawQuery());
            pedidos.add(new Pedido(ex.getRequestMethod(), caminho, ex.getRequestHeaders().getFirst("apikey"), corpo));
            Object[] r = rotas.getOrDefault(ex.getRequestMethod() + " " + caminho,
                    new Object[]{404, "{\"status\":404,\"error\":\"Not Found\"}"});
            byte[] b = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders((int) r[0], b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        servidor.start();
        String url = "127.0.0.1:" + servidor.getAddress().getPort();
        // como colado no Railway: sem http://, com barra no fim e espaço
        var props = new WhatsappProperties("evolution", "", "", "", "", " http://" + url + "/ ", " CHAVE ", "monitor");
        evo = new EvolutionSender(props, new ObjectMapper());
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    void rota(String chave, int status, String corpo) {
        rotas.put(chave, new Object[]{status, corpo});
    }

    @Test
    void converteIdsDeGrupo() {
        assertThat(EvolutionSender.jid("120363041234567890-group")).isEqualTo("120363041234567890@g.us");
        assertThat(EvolutionSender.jid("120363041234567890@g.us")).isEqualTo("120363041234567890@g.us");
        assertThat(EvolutionSender.jid("120363041234567890")).isEqualTo("120363041234567890@g.us");
        assertThat(EvolutionSender.jid("5511999998888")).isEqualTo("5511999998888");
    }

    @Test
    void urlSemProtocoloGanhaHttps() {
        var p = new WhatsappProperties("evolution", "", "", "", "", "evo-production.up.railway.app/", "k", "");
        assertThat(p.evolutionUrl()).isEqualTo("https://evo-production.up.railway.app");
        assertThat(p.evolutionInstancia()).isEqualTo("monitor");
        assertThat(p.evolutionConfigurada()).isTrue();
    }

    @Test
    void enviaTextoParaOGrupo() {
        rota("POST /message/sendText/monitor", 201, "{\"key\":{\"id\":\"ABC\"}}");
        evo.enviar("120363041234567890-group", "🔴 *Burger Moema* fechou");
        Pedido p = pedidos.get(0);
        assertThat(p.apikey()).isEqualTo("CHAVE");
        assertThat(p.corpo()).contains("\"number\":\"120363041234567890@g.us\"").contains("Burger Moema");
    }

    @Test
    void erroDeEnvioNaoRepeteEExplica() {
        rota("POST /message/sendText/monitor", 400, "{\"response\":{\"message\":[{\"exists\":false}]}}");
        assertThatThrownBy(() -> evo.enviar("123@g.us", "oi"))
                .isInstanceOf(WhatsappSender.FalhaEnvio.class)
                .hasMessageContaining("HTTP 400");
        assertThat(pedidos).hasSize(1);
    }

    @Test
    void listaGruposEmOrdemAlfabetica() {
        rota("GET /group/fetchAllGroups/monitor?getParticipants=false", 200,
                "[{\"id\":\"2@g.us\",\"subject\":\"Zona Sul\",\"size\":5},{\"id\":\"1@g.us\",\"subject\":\"admin SSD\"}]");
        assertThat(evo.listarGrupos()).extracting(WhatsappSender.GrupoWhatsapp::nome)
                .containsExactly("admin SSD", "Zona Sul");
        assertThat(evo.listarGrupos().get(0).id()).isEqualTo("1@g.us");
    }

    @Test
    void situacao() {
        rota("GET /instance/connectionState/monitor", 200, "{\"instance\":{\"instanceName\":\"monitor\",\"state\":\"open\"}}");
        assertThat(evo.situacao()).containsEntry("connected", true).containsEntry("provider", "evolution");
    }

    @Test
    void situacaoSemInstanciaAindaNaoEErro() {
        assertThat(evo.situacao()).containsEntry("connected", false).containsEntry("state", "sem_instancia")
                .doesNotContainKey("erro");
    }

    @Test
    void chaveErradaDizOQueConferir() {
        rota("GET /group/fetchAllGroups/monitor?getParticipants=false", 401, "{\"error\":\"Unauthorized\"}");
        assertThatThrownBy(() -> evo.listarGrupos()).hasMessageContaining("EVOLUTION_API_KEY");
    }

    @Test
    void conectarCriaAInstanciaNaPrimeiraVez() {
        rota("POST /instance/create", 201,
                "{\"instance\":{\"instanceName\":\"monitor\",\"status\":\"connecting\"},\"qrcode\":{\"pairingCode\":null,\"code\":\"2@x\",\"base64\":\"data:image/png;base64,QUJD\",\"count\":1}}");
        Map<String, Object> r = evo.conectar();
        assertThat(r).containsEntry("conectado", false).containsEntry("qrCode", "data:image/png;base64,QUJD");
        Pedido criar = pedidos.get(1);
        assertThat(criar.corpo()).contains("\"instanceName\":\"monitor\"").contains("WHATSAPP-BAILEYS")
                .contains("\"qrcode\":true");
    }

    @Test
    void conectarRetomaQrQuandoJaExiste() {
        rota("GET /instance/connectionState/monitor", 200, "{\"instance\":{\"instanceName\":\"monitor\",\"state\":\"close\"}}");
        rota("GET /instance/connect/monitor", 200, "{\"pairingCode\":\"WZYEH1YY\",\"code\":\"2@y\",\"base64\":\"REVG\",\"count\":2}");
        Map<String, Object> r = evo.conectar();
        assertThat(r).containsEntry("qrCode", "data:image/png;base64,REVG").containsEntry("codigoPareamento", "WZYEH1YY");
    }

    @Test
    void conectarQuandoJaConectado() {
        rota("GET /instance/connectionState/monitor", 200, "{\"instance\":{\"instanceName\":\"monitor\",\"state\":\"open\"}}");
        assertThat(evo.conectar()).containsEntry("conectado", true);
        assertThat(pedidos).hasSize(1);
    }
}
