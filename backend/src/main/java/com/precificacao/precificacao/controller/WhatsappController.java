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
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Grupos de WhatsApp e o mapa grupo → lojas. */
@RestController
@RequestMapping("/whatsapp")
public class WhatsappController {

    public record GrupoRequest(String nome, String chatId, boolean todasLojas, boolean admin,
                               Boolean avisarRetorno, Boolean ativo, List<String> lojas) {
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

    /** Grupos em que o número conectado na Z-API participa (para pegar o ID do grupo). */
    @GetMapping("/grupos-disponiveis")
    public List<WhatsappSender.GrupoWhatsapp> disponiveis() {
        return whatsapp.listarGrupos();
    }

    @GetMapping("/situacao")
    public Map<String, Object> situacao() {
        return whatsapp.situacao();
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
            whatsapp.enviar(grupo.chatId(), texto);
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
                req.avisarRetorno() == null || req.avisarRetorno(), req.ativo() == null || req.ativo(), req.lojas());
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
