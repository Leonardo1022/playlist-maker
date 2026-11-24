package br.com.playlistmaker.core.dominio.excecoes;

public class FalhaComunicacaoException extends RuntimeException {
    public FalhaComunicacaoException(String mensagem) {
        super(mensagem);
    }

    public FalhaComunicacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
