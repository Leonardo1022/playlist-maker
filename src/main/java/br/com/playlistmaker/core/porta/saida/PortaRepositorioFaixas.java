package br.com.playlistmaker.core.porta.saida;

import br.com.playlistmaker.core.dominio.FaixaCurtida;
import java.time.Instant;
import java.util.List;

public interface PortaRepositorioFaixas {
    void salvarTodas(List<FaixaCurtida> faixas);
    int removerAusentes(Instant sincronizadaAntesDe);
    int contarTotal();
    List<FaixaCurtida> listarPaginado(int deslocamento, int limite);
}
