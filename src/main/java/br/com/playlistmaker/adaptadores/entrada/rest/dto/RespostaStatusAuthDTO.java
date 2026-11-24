package br.com.playlistmaker.adaptadores.entrada.rest.dto;

public record RespostaStatusAuthDTO(
        boolean conectado,
        String status,
        String usuarioId,
        String conectadoEm,
        String mensagem
) {}
