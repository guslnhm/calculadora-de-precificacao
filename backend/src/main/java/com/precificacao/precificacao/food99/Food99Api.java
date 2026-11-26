package com.precificacao.precificacao.food99;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Uma chamada POST com corpo JSON à 99Food; devolve a resposta inteira (errno, errmsg, data). */
@FunctionalInterface
public interface Food99Api {
    JsonNode post(String caminho, Map<String, Object> corpo, String bearer);
}
