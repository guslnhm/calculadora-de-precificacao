package com.precificacao.precificacao.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Não envia nada: só escreve no log. Usado quando a Z-API não está configurada e nos testes. */
public class ConsoleSender implements WhatsappSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSender.class);

    public record Enviada(String chatId, String texto) {
    }

    public final List<Enviada> enviadas = new CopyOnWriteArrayList<>();

    @Override
    public void enviar(String chatId, String texto) {
        enviadas.add(new Enviada(chatId, texto));
        log.info("[WhatsApp → {}]\n{}", chatId, texto);
    }

    @Override
    public List<GrupoWhatsapp> listarGrupos() {
        return List.of();
    }

    @Override
    public Map<String, Object> situacao() {
        return Map.of("provider", "console", "connected", false);
    }

    public List<String> para(String chatId) {
        return enviadas.stream().filter(e -> e.chatId().equals(chatId)).map(Enviada::texto).toList();
    }
}
