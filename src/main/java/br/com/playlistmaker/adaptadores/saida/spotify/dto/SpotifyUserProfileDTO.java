package br.com.playlistmaker.adaptadores.saida.spotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpotifyUserProfileDTO(
        String id,
        @JsonProperty("display_name") String displayName,
        String product
) {}
