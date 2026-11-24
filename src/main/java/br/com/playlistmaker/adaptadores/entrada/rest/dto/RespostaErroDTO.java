package br.com.playlistmaker.adaptadores.entrada.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RespostaErroDTO(
        String erro,
        String mensagem,
        Integer tempoEsperaSegundos
) {
    public RespostaErroDTO(String erro, String mensagem) {
        this(erro, mensagem, null);
    }
}
