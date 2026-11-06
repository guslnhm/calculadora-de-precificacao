package com.precificacao.precificacao.whatsapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZapiSenderTest {

    HttpServer servidor;
    final List<String> caminhos = new ArrayList<>();
    final List<String> clientTokens = new ArrayList<>();

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    String subir(int status, String corpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            caminhos.add(ex.getRequestURI().getPath());
            clientTokens.add(ex.getRequestHeaders().getFirst("Client-Token"));
            byte[] b = corpo.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @Test
    void limpaEspacosEAspasDasVariaveis() {
        var p = new WhatsappProperties(" zapi\n", " \"INST123\" ", "TOK456 \n", "'CT789'", "  ", "", "", "");
        assertThat(p.provider()).isEqualTo("zapi");
        assertThat(p.zapiInstanceId()).isEqualTo("INST123");
        assertThat(p.zapiToken()).isEqualTo("TOK456");
        assertThat(p.zapiClientToken()).isEqualTo("CT789");
        assertThat(p.zapiBaseUrl()).isEqualTo("https://api.z-api.io");
        assertThat(p.zapiConfigurada()).isTrue();
    }

    @Test
    void situacaoComCredenciaisColadasComEspaco() throws IOException {
        String url = subir(200, "{\"connected\":true,\"smartphoneConnected\":true}");
        var z = new ZapiSender(new WhatsappProperties("zapi", "INST \n", " TOK", "CT ", url, "", "", ""), new ObjectMapper());
        Map<String, Object> s = z.situacao();
        assertThat(s).containsEntry("connected", true).containsEntry("provider", "zapi").doesNotContainKey("erro");
        assertThat(caminhos).containsExactly("/instances/INST/token/TOK/status");
        assertThat(clientTokens).containsExactly("CT");
    }

    @Test
    void situacaoMostraOErroDaZapiEmVezDeExplodir() throws IOException {
        String url = subir(400, "{\"error\":\"your client-token is not configured\"}");
        var z = new ZapiSender(new WhatsappProperties("zapi", "INST", "TOK", "", url, "", "", ""), new ObjectMapper());
        assertThat(z.situacao().get("erro").toString())
                .contains("HTTP 400").contains("client-token is not configured");
    }

    @Test
    void enviarFalhaComMensagemClara() throws IOException {
        String url = subir(401, "{\"error\":\"unauthorized\"}");
        var z = new ZapiSender(new WhatsappProperties("zapi", "INST", "TOK", "", url, "", "", ""), new ObjectMapper());
        assertThatThrownBy(() -> z.enviar("123-group", "oi"))
                .isInstanceOf(WhatsappSender.FalhaEnvio.class)
                .hasMessageContaining("HTTP 401");
        assertThat(caminhos).hasSize(1); // erro de credencial não é repetido
    }
}
