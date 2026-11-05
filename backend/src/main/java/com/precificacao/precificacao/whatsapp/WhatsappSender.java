package com.precificacao.precificacao.whatsapp;

import java.util.List;
import java.util.Map;

/** Envia mensagens para grupos de WhatsApp. */
public interface WhatsappSender {

    /** Grupo encontrado no WhatsApp: id (chat) e nome. */
    record GrupoWhatsapp(String id, String nome) {
    }

    void enviar(String chatId, String texto);

    List<GrupoWhatsapp> listarGrupos();

    /** Situação da conexão (ex.: connected, smartphoneConnected). */
    Map<String, Object> situacao();

    class FalhaEnvio extends RuntimeException {
        public FalhaEnvio(String mensagem) {
            super(mensagem);
        }

        public FalhaEnvio(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
