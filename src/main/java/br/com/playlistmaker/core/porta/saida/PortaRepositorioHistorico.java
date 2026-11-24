package br.com.playlistmaker.core.porta.saida;

import br.com.playlistmaker.core.dominio.RegistroSincronizacao;
import java.util.Optional;

public interface PortaRepositorioHistorico {
    RegistroSincronizacao salvar(RegistroSincronizacao registro);
    Optional<RegistroSincronizacao> obterUltimo();
    Optional<RegistroSincronizacao> obterPorId(Long id);
}
