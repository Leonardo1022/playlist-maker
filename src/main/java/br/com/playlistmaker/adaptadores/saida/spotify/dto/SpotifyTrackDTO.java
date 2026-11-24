package br.com.playlistmaker.adaptadores.saida.spotify.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SpotifyTrackDTO(
        String id,
        String name,
        List<SpotifyArtistDTO> artists,
        SpotifyAlbumDTO album,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("is_local") Boolean isLocal
) {}
