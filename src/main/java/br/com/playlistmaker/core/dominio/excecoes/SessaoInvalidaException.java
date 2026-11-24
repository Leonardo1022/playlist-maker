package br.com.playlistmaker.core.dominio.excecoes;

public class SessaoInvalidaException extends RuntimeException {
    public SessaoInvalidaException(String mensagem) {
        super(mensagem);
    }
}
