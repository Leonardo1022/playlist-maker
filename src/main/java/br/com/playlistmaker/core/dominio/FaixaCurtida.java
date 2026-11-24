package br.com.playlistmaker.core.dominio;

import java.time.Instant;
import java.util.Objects;

public class FaixaCurtida {
    private final String id;
    private final String titulo;
    private final String artistas;
    private final String album;
    private final long duracaoMs;
    private final Instant curtidaEm;
    private final Instant sincronizadaEm;
    private final String fonte;

    public FaixaCurtida(String id, String titulo, String artistas, String album,
                        long duracaoMs, Instant curtidaEm, Instant sincronizadaEm, String fonte) {
        this.id = Objects.requireNonNull(id, "id nao pode ser nulo");
        this.titulo = Objects.requireNonNull(titulo, "titulo nao pode ser nulo");
        this.artistas = Objects.requireNonNull(artistas, "artistas nao pode ser nulo");
        this.album = Objects.requireNonNull(album, "album nao pode ser nulo");
        if (duracaoMs <= 0) {
            throw new IllegalArgumentException("duracaoMs deve ser positiva");
        }
        this.duracaoMs = duracaoMs;
        this.curtidaEm = Objects.requireNonNull(curtidaEm, "curtidaEm nao pode ser nulo");
        this.sincronizadaEm = Objects.requireNonNull(sincronizadaEm, "sincronizadaEm nao pode ser nulo");
        this.fonte = (fonte == null || fonte.isBlank()) ? "spotify" : fonte;
    }

    public FaixaCurtida(String id, String titulo, String artistas, String album,
                        long duracaoMs, Instant curtidaEm, Instant sincronizadaEm) {
        this(id, titulo, artistas, album, duracaoMs, curtidaEm, sincronizadaEm, "spotify");
    }

    public String getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getArtistas() { return artistas; }
    public String getAlbum() { return album; }
    public long getDuracaoMs() { return duracaoMs; }
    public Instant getCurtidaEm() { return curtidaEm; }
    public Instant getSincronizadaEm() { return sincronizadaEm; }
    public String getFonte() { return fonte; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FaixaCurtida that = (FaixaCurtida) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
