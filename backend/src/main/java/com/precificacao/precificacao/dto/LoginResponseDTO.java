package com.precificacao.precificacao.dto;

import java.util.List;

public class LoginResponseDTO {

    private String token;
    private String nome;
    private String perfil;
    private List<String> lojas;
    private boolean trocarSenha;

    public LoginResponseDTO() {
    }

    public LoginResponseDTO(String token, String nome, String perfil, List<String> lojas, boolean trocarSenha) {
        this.token = token;
        this.nome = nome;
        this.perfil = perfil;
        this.lojas = lojas;
        this.trocarSenha = trocarSenha;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getPerfil() {
        return perfil;
    }

    public void setPerfil(String perfil) {
        this.perfil = perfil;
    }

    public List<String> getLojas() {
        return lojas;
    }

    public void setLojas(List<String> lojas) {
        this.lojas = lojas;
    }

    public boolean isTrocarSenha() {
        return trocarSenha;
    }

    public void setTrocarSenha(boolean trocarSenha) {
        this.trocarSenha = trocarSenha;
    }
}
