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

    /**
     * Inicia/retoma a conexão do número e devolve o QR Code para ler no celular, quando o provedor permite
     * (chaves: conectado, qrCode em data URL, codigoPareamento, mensagem).
     */
    default Map<String, Object> conectar() {
        return Map.of("conectado", false,
                "mensagem", "Este provedor não conecta pelo site: leia o QR Code no painel do próprio provedor.");
    }

    class FalhaEnvio extends RuntimeException {
        public FalhaEnvio(String mensagem) {
            super(mensagem);
        }

        public FalhaEnvio(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
