package br.com.playlistmaker.adaptadores.saida.spotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SpotifyTokenResponseDTO(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("scope") String scope,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("refresh_token") String refreshToken
) {}
