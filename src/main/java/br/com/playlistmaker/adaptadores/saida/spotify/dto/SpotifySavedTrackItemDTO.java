package br.com.playlistmaker.adaptadores.saida.spotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpotifySavedTrackItemDTO(
        @JsonProperty("added_at") String addedAt,
        SpotifyTrackDTO track
) {}
