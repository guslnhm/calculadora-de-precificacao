package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.monitor.GrupoWhatsappRepository;
import com.precificacao.precificacao.monitor.MonitorModelos.Grupo;
import com.precificacao.precificacao.monitor.MonitorRepository;
import com.precificacao.precificacao.whatsapp.WhatsappSender;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Grupos de WhatsApp e o mapa grupo → lojas. */
@RestController
@RequestMapping("/whatsapp")
public class WhatsappController {

    public record GrupoRequest(String nome, String chatId, boolean todasLojas, boolean admin,
                               Boolean avisarRetorno, Boolean ativo, List<String> lojas, String instancia) {
    }

    public record NumeroRequest(String nome) {
    }

    private final GrupoWhatsappRepository grupos;
    private final WhatsappSender whatsapp;
    private final MonitorRepository repo;
    private final Clock clock;

    public WhatsappController(GrupoWhatsappRepository grupos, WhatsappSender whatsapp, MonitorRepository repo, Clock clock) {
        this.grupos = grupos;
        this.whatsapp = whatsapp;
        this.repo = repo;
        this.clock = clock;
    }

    @GetMapping("/grupos")
    public List<Grupo> listar() {
        return grupos.listar(false);
    }

    @PostMapping("/grupos")
    public ResponseEntity<?> criar(@RequestBody GrupoRequest req) {
        return salvar(null, req);
    }

    @PutMapping("/grupos/{id}")
    public ResponseEntity<?> atualizar(@PathVariable long id, @RequestBody GrupoRequest req) {
        if (grupos.buscar(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return salvar(id, req);
    }

    @DeleteMapping("/grupos/{id}")
    public void remover(@PathVariable long id) {
        grupos.remover(id);
    }

    /** Grupos em que o número participa (para pegar o ID do grupo). instancia vazia = número principal. */
    @GetMapping("/grupos-disponiveis")
    public List<WhatsappSender.GrupoWhatsapp> disponiveis(@RequestParam(required = false) String instancia) {
        return whatsapp.listarGrupos(instancia);
    }

    @GetMapping("/situacao")
    public Map<String, Object> situacao(@RequestParam(required = false) String instancia) {
        return whatsapp.situacao(instancia);
    }

    /** Conecta o número: devolve o QR Code para ler no WhatsApp do celular (ou conectado=true). */
    @PostMapping("/conectar")
    public Map<String, Object> conectar(@RequestParam(required = false) String instancia) {
        return whatsapp.conectar(instancia);
    }

    // ------------------------------------------------------------------ números

    /** Números que enviam: o principal (variável de ambiente) e os extras cadastrados aqui. */
    @GetMapping("/numeros")
    public Map<String, Object> numeros() {
        List<Map<String, Object>> lista = new ArrayList<>();
        List<Grupo> todos = grupos.listar(false);
        Map<String, Object> principal = new LinkedHashMap<>();
        principal.put("instancia", null);
        principal.put("nome", "Número principal");
        principal.put("principal", true);
        principal.put("grupos", todos.stream().filter(g -> g.instancia() == null).count());
        lista.add(principal);
        for (GrupoWhatsappRepository.Numero n : grupos.numeros()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("instancia", n.instancia());
            m.put("nome", n.nome());
            m.put("principal", false);
            m.put("grupos", n.grupos());
            lista.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("variosNumeros", whatsapp.variosNumeros());
        out.put("instanciaPrincipal", whatsapp.instanciaPrincipal());
        out.put("numeros", lista);
        return out;
    }

    @PostMapping("/numeros")
    public ResponseEntity<?> criarNumero(@RequestBody NumeroRequest req) {
        if (!whatsapp.variosNumeros()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "NAO_SUPORTADO",
                    "mensagem", "Mais de um número só funciona com a Evolution API"));
        }
        String nome = req.nome() == null ? "" : req.nome().strip();
        if (nome.isEmpty() || nome.length() > 120) {
            return ResponseEntity.badRequest().body(Map.of("erro", "DADOS_INVALIDOS", "mensagem", "Dê um nome para o número"));
        }
        String instancia = instanciaLivre(nome);
        grupos.criarNumero(instancia, nome);
        return ResponseEntity.ok(Map.of("instancia", instancia, "nome", nome));
    }

    /** Tira o número da lista; os grupos dele voltam para o principal. */
    @DeleteMapping("/numeros/{instancia}")
    public Map<String, Object> removerNumero(@PathVariable String instancia) {
        grupos.removerNumero(instancia);
        return Map.of("ok", true);
    }

    /** Passa todos os grupos para este número (vazio = o principal). */
    @PostMapping("/numeros/mover-todos")
    public Map<String, Object> moverTodos(@RequestParam(required = false) String instancia) {
        String inst = instancia == null || instancia.isBlank() ? null : instancia.strip();
        if (inst != null && grupos.numeros().stream().noneMatch(n -> n.instancia().equals(inst))) {
            throw new IllegalArgumentException("Número não encontrado: " + inst);
        }
        return Map.of("grupos", grupos.moverTodos(inst));
    }

    /** "Número teste 2" → "monitor-numero-teste-2" (sem repetir uma que já existe). */
    String instanciaLivre(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "numero";
        }
        base = "monitor-" + (base.length() > 40 ? base.substring(0, 40) : base);
        String principal = whatsapp.instanciaPrincipal();
        List<String> usadas = new ArrayList<>(grupos.numeros().stream().map(GrupoWhatsappRepository.Numero::instancia).toList());
        if (principal != null) {
            usadas.add(principal);
        }
        String c = base;
        for (int i = 2; usadas.contains(c); i++) {
            c = base + "-" + i;
        }
        return c;
    }

    /** Manda uma mensagem de teste para o grupo (envia de verdade, mesmo no modo sombra). */
    @PostMapping("/grupos/{id}/teste")
    public ResponseEntity<?> teste(@PathVariable long id) {
        var g = grupos.buscar(id);
        if (g.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Grupo grupo = g.get();
        String lojas = grupo.todasLojas() ? "todas as lojas" : grupo.lojas().size() + " loja(s)";
        String texto = "🧪 *Teste do monitor iFood*\nEste grupo (" + grupo.nome() + ") vai receber os alertas de "
                + lojas + ".\nNenhuma ação é necessária.";
        try {
            whatsapp.enviar(grupo.instancia(), grupo.chatId(), texto);
            repo.registrarEnvio("teste", grupo.nome(), grupo.chatId(), List.of(), texto, "ENVIADO", null, OffsetDateTime.now(clock));
            return ResponseEntity.ok(Map.of("enviado", true));
        } catch (RuntimeException e) {
            repo.registrarEnvio("teste", grupo.nome(), grupo.chatId(), List.of(), texto, "FALHOU", e.getMessage(), OffsetDateTime.now(clock));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("enviado", false, "erro", e.getMessage()));
        }
    }

    private ResponseEntity<?> salvar(Long id, GrupoRequest req) {
        if (req.nome() == null || req.nome().isBlank() || req.chatId() == null || req.chatId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("erro", "DADOS_INVALIDOS",
                    "mensagem", "Informe o nome do grupo e o ID do grupo no WhatsApp"));
        }
        long gid = grupos.salvar(id, req.nome().trim(), req.chatId().trim(), req.todasLojas(), req.admin(),
                req.avisarRetorno() == null || req.avisarRetorno(), req.ativo() == null || req.ativo(), req.lojas(),
                req.instancia());
        return ResponseEntity.ok(grupos.buscar(gid).orElseThrow());
    }

    /** Mostra na tela o motivo real do erro (em vez de "erro inesperado"). */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> erro(RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Erro no WhatsApp", e);
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", "WHATSAPP", "mensagem", msg));
    }
}
