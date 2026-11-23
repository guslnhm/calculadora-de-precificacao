package com.precificacao.precificacao.whatsapp;

import java.util.List;
import java.util.Map;

/** Envia mensagens para grupos de WhatsApp. */
public interface WhatsappSender {

    /** Grupo encontrado no WhatsApp: id (chat) e nome. */
    record GrupoWhatsapp(String id, String nome) {
    }

    void enviar(String chatId, String texto);

    // --- Vários números (só a Evolution: cada número é uma "instância"). instancia null = número principal.

    /** O provedor aceita mais de um número? */
    default boolean variosNumeros() {
        return false;
    }

    /** Nome da instância do número principal (da variável de ambiente). */
    default String instanciaPrincipal() {
        return null;
    }

    default void enviar(String instancia, String chatId, String texto) {
        enviar(chatId, texto);
    }

    default List<GrupoWhatsapp> listarGrupos(String instancia) {
        return listarGrupos();
    }

    default Map<String, Object> situacao(String instancia) {
        return situacao();
    }

    default Map<String, Object> conectar(String instancia) {
        return conectar();
    }

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
