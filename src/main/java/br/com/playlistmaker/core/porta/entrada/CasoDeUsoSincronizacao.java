package br.com.playlistmaker.core.porta.entrada;

import br.com.playlistmaker.core.dominio.FaixaCurtida;
import br.com.playlistmaker.core.dominio.RegistroSincronizacao;
import java.util.List;

public interface CasoDeUsoSincronizacao {
    void dispararSincronizacaoSobDemanda();
    RegistroSincronizacao obterStatusAtual();
    List<FaixaCurtida> consultarCurtidas(int deslocamento, int limite);
    int contarCurtidas();
}
