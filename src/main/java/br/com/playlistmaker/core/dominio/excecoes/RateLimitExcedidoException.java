package br.com.playlistmaker.core.dominio.excecoes;

public class RateLimitExcedidoException extends RuntimeException {
    private final int tempoEsperaSegundos;

    public RateLimitExcedidoException(String mensagem, int tempoEsperaSegundos) {
        super(mensagem);
        this.tempoEsperaSegundos = tempoEsperaSegundos;
    }

    public int getTempoEsperaSegundos() {
        return tempoEsperaSegundos;
    }
}
