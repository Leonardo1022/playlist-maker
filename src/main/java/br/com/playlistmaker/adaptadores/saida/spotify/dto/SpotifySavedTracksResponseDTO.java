package br.com.playlistmaker.adaptadores.saida.spotify.dto;

import java.util.List;

public record SpotifySavedTracksResponseDTO(
        Integer total,
        Integer limit,
        Integer offset,
        String next,
        List<SpotifySavedTrackItemDTO> items
) {}
