package br.com.playlistmaker.core.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FaixaCurtidaTest {

    private static final Instant AGORA = Instant.parse("2026-11-24T07:00:00Z");
    private static final Instant CURTIDA_EM = Instant.parse("2026-01-15T14:30:00Z");

    @Test
    void deveCriarFaixaComTodosOsCampos() {
        FaixaCurtida faixa = new FaixaCurtida(
                "7ouMYWpwJ422jRcDASZB7P",
                "Knights of Cydonia",
                "Muse",
                "Black Holes and Revelations",
                366213L,
                CURTIDA_EM,
                AGORA,
                "spotify"
        );

        assertThat(faixa.getId()).isEqualTo("7ouMYWpwJ422jRcDASZB7P");
        assertThat(faixa.getTitulo()).isEqualTo("Knights of Cydonia");
        assertThat(faixa.getArtistas()).isEqualTo("Muse");
        assertThat(faixa.getAlbum()).isEqualTo("Black Holes and Revelations");
        assertThat(faixa.getDuracaoMs()).isEqualTo(366213L);
        assertThat(faixa.getCurtidaEm()).isEqualTo(CURTIDA_EM);
        assertThat(faixa.getSincronizadaEm()).isEqualTo(AGORA);
        assertThat(faixa.getFonte()).isEqualTo("spotify");
    }

    @Test
    void deveCriarFaixaSemFonteUsandoPadraoSpotify() {
        FaixaCurtida faixa = new FaixaCurtida(
                "id-001", "Titulo", "Artista", "Album", 180000L, CURTIDA_EM, AGORA
        );
        assertThat(faixa.getFonte()).isEqualTo("spotify");
    }

    @Test
    void deveUsarFontePadraoQuandoFonteNulaOuVazia() {
        FaixaCurtida com_null = new FaixaCurtida("id", "Titulo", "Artista", "Album", 1000L, CURTIDA_EM, AGORA, null);
        assertThat(com_null.getFonte()).isEqualTo("spotify");

        FaixaCurtida com_vazio = new FaixaCurtida("id", "Titulo", "Artista", "Album", 1000L, CURTIDA_EM, AGORA, "   ");
        assertThat(com_vazio.getFonte()).isEqualTo("spotify");
    }

    @Test
    void deveRejeitarIdNulo() {
        assertThatThrownBy(() ->
                new FaixaCurtida(null, "Titulo", "Artista", "Album", 180000L, CURTIDA_EM, AGORA)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void deveRejeitarTituloNulo() {
        assertThatThrownBy(() ->
                new FaixaCurtida("id", null, "Artista", "Album", 180000L, CURTIDA_EM, AGORA)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("titulo");
    }

    @Test
    void deveRejeitarArtistasNulo() {
        assertThatThrownBy(() ->
                new FaixaCurtida("id", "Titulo", null, "Album", 180000L, CURTIDA_EM, AGORA)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("artistas");
    }

    @Test
    void deveRejeitarAlbumNulo() {
        assertThatThrownBy(() ->
                new FaixaCurtida("id", "Titulo", "Artista", null, 180000L, CURTIDA_EM, AGORA)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("album");
    }

    @Test
    void deveRejeitarCurtidaEmNulo() {
        assertThatThrownBy(() ->
                new FaixaCurtida("id", "Titulo", "Artista", "Album", 180000L, null, AGORA)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("curtidaEm");
    }

    @Test
    void deveRejeitarSincronizadaEmNulo() {
        assertThatThrownBy(() ->
                new FaixaCurtida("id", "Titulo", "Artista", "Album", 180000L, CURTIDA_EM, null)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sincronizadaEm");
    }

    @Test
    void deveRejeitarDuracaoZeroOuNegativa() {
        assertThatThrownBy(() ->
                new FaixaCurtida("id", "Titulo", "Artista", "Album", 0L, CURTIDA_EM, AGORA)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duracaoMs deve ser positiva");

        assertThatThrownBy(() ->
                new FaixaCurtida("id", "Titulo", "Artista", "Album", -1L, CURTIDA_EM, AGORA)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveImplementarIgualdadePorId() {
        FaixaCurtida faixa1 = new FaixaCurtida("MESMO_ID", "Titulo A", "Artista A", "Album A", 100L, CURTIDA_EM, AGORA);
        FaixaCurtida faixa2 = new FaixaCurtida("MESMO_ID", "Titulo B", "Artista B", "Album B", 200L, CURTIDA_EM, AGORA);
        FaixaCurtida faixa3 = new FaixaCurtida("OUTRO_ID", "Titulo A", "Artista A", "Album A", 100L, CURTIDA_EM, AGORA);

        assertThat(faixa1).isEqualTo(faixa2);
        assertThat(faixa1).isNotEqualTo(faixa3);
    }

    @Test
    void deveImplementarHashCodePorId() {
        FaixaCurtida faixa1 = new FaixaCurtida("MESMO_ID", "Titulo A", "Artista A", "Album A", 100L, CURTIDA_EM, AGORA);
        FaixaCurtida faixa2 = new FaixaCurtida("MESMO_ID", "Titulo B", "Artista B", "Album B", 200L, CURTIDA_EM, AGORA);

        assertThat(faixa1.hashCode()).isEqualTo(faixa2.hashCode());
    }

    @Test
    void deveNaoSerIgualANull() {
        FaixaCurtida faixa = new FaixaCurtida("id", "Titulo", "Artista", "Album", 100L, CURTIDA_EM, AGORA);
        assertThat(faixa).isNotEqualTo(null);
    }

    @Test
    void deveSerIgualAMesmaInstancia() {
        FaixaCurtida faixa = new FaixaCurtida("id", "Titulo", "Artista", "Album", 100L, CURTIDA_EM, AGORA);
        assertThat(faixa).isEqualTo(faixa);
    }
}
