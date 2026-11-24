package br.com.playlistmaker.core.dominio;

import java.time.Instant;
import java.util.Objects;

public class RegistroSincronizacao {
    private final Long id;
    private final Instant iniciadoEm;
    private final Instant finalizadoEm;
    private final StatusSincronizacao status;
    private final int totalCurtidas;
    private final int novasFaixas;
    private final int removidasFaixas;
    private final String mensagem;
    private final Integer tempoEsperaSegundos;

    public RegistroSincronizacao(Long id, Instant iniciadoEm, Instant finalizadoEm,
                                 StatusSincronizacao status, int totalCurtidas, int novasFaixas,
                                 int removidasFaixas, String mensagem, Integer tempoEsperaSegundos) {
        this.id = id;
        this.iniciadoEm = Objects.requireNonNull(iniciadoEm, "iniciadoEm nao pode ser nulo");
        this.finalizadoEm = finalizadoEm;
        this.status = Objects.requireNonNull(status, "status nao pode ser nulo");
        this.totalCurtidas = totalCurtidas;
        this.novasFaixas = novasFaixas;
        this.removidasFaixas = removidasFaixas;
        this.mensagem = mensagem;
        this.tempoEsperaSegundos = tempoEsperaSegundos;
    }

    public static RegistroSincronizacao iniciar(Instant agora) {
        return new RegistroSincronizacao(null, agora, null, StatusSincronizacao.EM_ANDAMENTO, 0, 0, 0, "Sincronizacao iniciada", null);
    }

    public RegistroSincronizacao concluir(Instant finalizadoEm, int total, int novas, int removidas, String mensagem) {
        return new RegistroSincronizacao(this.id, this.iniciadoEm, finalizadoEm, StatusSincronizacao.CONCLUIDO, total, novas, removidas, mensagem, null);
    }

    public RegistroSincronizacao limitar(Instant finalizadoEm, int tempoEsperaSegundos, String mensagem) {
        return new RegistroSincronizacao(this.id, this.iniciadoEm, finalizadoEm, StatusSincronizacao.LIMITADO, this.totalCurtidas, this.novasFaixas, this.removidasFaixas, mensagem, tempoEsperaSegundos);
    }

    public RegistroSincronizacao falhar(Instant finalizadoEm, String mensagem) {
        return new RegistroSincronizacao(this.id, this.iniciadoEm, finalizadoEm, StatusSincronizacao.ERRO, this.totalCurtidas, this.novasFaixas, this.removidasFaixas, mensagem, null);
    }

    public Long getId() { return id; }
    public Instant getIniciadoEm() { return iniciadoEm; }
    public Instant getFinalizadoEm() { return finalizadoEm; }
    public StatusSincronizacao getStatus() { return status; }
    public int getTotalCurtidas() { return totalCurtidas; }
    public int getNovasFaixas() { return novasFaixas; }
    public int getRemovidasFaixas() { return removidasFaixas; }
    public String getMensagem() { return mensagem; }
    public Integer getTempoEsperaSegundos() { return tempoEsperaSegundos; }
}
