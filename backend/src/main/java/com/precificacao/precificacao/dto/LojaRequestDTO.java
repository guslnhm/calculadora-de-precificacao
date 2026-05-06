package com.precificacao.precificacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LojaRequestDTO {

    @NotBlank(message = "O nome da loja é obrigatório")
    @Size(max = 150, message = "O nome da loja deve ter no máximo 150 caracteres")
    private String nome;

    @Size(max=150, message = "O nome do cliente deve ter no máximo 150 caracteres")
    private String clienteNome;

    private String observacao;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }
}