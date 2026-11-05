package com.precificacao.precificacao.ifood;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * iFood falso para os testes: um servidor HTTP local que devolve respostas
 * pré-definidas por "MÉTODO /caminho" e registra cada requisição recebida.
 * Quando há várias respostas para a mesma rota, devolve uma por chamada e
 * repete a última.
 */
public class FakeIfood implements AutoCloseable {

    public record Requisicao(String metodo, String caminho, String query, String autorizacao, String corpo) {
    }

    record Resposta(int status, String corpo, String contentType, Map<String, String> cabecalhos) {
    }

    private final HttpServer server;
    public final List<Requisicao> requisicoes = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Resposta>> rotas = new ConcurrentHashMap<>();
    private volatile int tokensEmitidos = 0;
    private volatile int expiresIn = 21600;

    public FakeIfood() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::atender);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public IfoodProperties propriedades() {
        return new IfoodProperties("client-id", "client-secret", url(), "America/Sao_Paulo", 5, 4);
    }

    public IfoodHttp http(List<Long> esperas) {
        IfoodHttp h = new IfoodHttp(propriedades(), new ObjectMapper());
        h.setEspera(esperas::add);
        return h;
    }

    public FakeIfood quando(String metodo, String caminho, int status, String corpo) {
        return quando(metodo, caminho, status, corpo, Map.of());
    }

    public FakeIfood quando(String metodo, String caminho, int status, String corpo, Map<String, String> cabecalhos) {
        String tipo = corpo != null && (corpo.startsWith("{") || corpo.startsWith("[")) ? "application/json" : "text/plain";
        rotas.computeIfAbsent(metodo + " " + caminho, k -> new ArrayDeque<>())
                .add(new Resposta(status, corpo == null ? "" : corpo, tipo, cabecalhos));
        return this;
    }

    public FakeIfood quandoArquivo(String metodo, String caminho, String arquivo) {
        return quando(metodo, caminho, 200, recurso(arquivo));
    }

    public int tokensEmitidos() {
        return tokensEmitidos;
    }

    public void setExpiresIn(int segundos) {
        this.expiresIn = segundos;
    }

    /** Requisições feitas a um caminho (sem contar as de token). */
    public List<Requisicao> chamadas(String metodo, String caminho) {
        return requisicoes.stream().filter(r -> r.metodo().equals(metodo) && r.caminho().equals(caminho)).toList();
    }

    public static String recurso(String nome) {
        try (InputStream in = FakeIfood.class.getResourceAsStream("/ifood/" + nome)) {
            if (in == null) {
                throw new IllegalArgumentException("Recurso de teste não encontrado: " + nome);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void atender(HttpExchange ex) throws IOException {
        String metodo = ex.getRequestMethod();
        String caminho = ex.getRequestURI().getPath();
        String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if ("POST".equals(metodo) && caminho.equals("/authentication/v1.0/oauth/token")) {
            tokensEmitidos++;
            responder(ex, new Resposta(200,
                    "{\"accessToken\":\"tok" + tokensEmitidos + "\",\"type\":\"bearer\",\"expiresIn\":" + expiresIn + "}",
                    "application/json", Map.of()));
            return;
        }

        requisicoes.add(new Requisicao(metodo, caminho, ex.getRequestURI().getRawQuery(),
                ex.getRequestHeaders().getFirst("Authorization"), corpo));

        Deque<Resposta> fila = rotas.get(metodo + " " + caminho);
        Resposta r;
        if (fila == null || fila.isEmpty()) {
            r = new Resposta(404, "{\"message\":\"rota não configurada no FakeIfood: " + metodo + " " + caminho + "\"}",
                    "application/json", Map.of());
        } else {
            synchronized (fila) {
                r = fila.size() > 1 ? fila.poll() : fila.peek();
            }
        }
        responder(ex, r);
    }

    private static void responder(HttpExchange ex, Resposta r) throws IOException {
        byte[] bytes = r.corpo().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", r.contentType() + "; charset=utf-8");
        r.cabecalhos().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        if (bytes.length == 0) {
            ex.sendResponseHeaders(r.status(), -1);
        } else {
            ex.sendResponseHeaders(r.status(), bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
