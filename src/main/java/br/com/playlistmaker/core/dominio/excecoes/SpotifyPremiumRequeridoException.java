package br.com.playlistmaker.core.dominio.excecoes;

public class SpotifyPremiumRequeridoException extends RuntimeException {
    public SpotifyPremiumRequeridoException(String mensagem) {
        super(mensagem);
    }
}
