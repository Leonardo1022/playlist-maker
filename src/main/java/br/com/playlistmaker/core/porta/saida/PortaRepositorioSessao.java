package br.com.playlistmaker.core.porta.saida;

import br.com.playlistmaker.core.dominio.SessaoUsuario;
import java.util.Optional;

public interface PortaRepositorioSessao {
    void salvar(SessaoUsuario sessao);
    Optional<SessaoUsuario> obter();
    void remover();
}
