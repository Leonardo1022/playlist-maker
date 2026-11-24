package br.com.playlistmaker.core.porta.entrada;

import br.com.playlistmaker.core.dominio.SessaoUsuario;

public interface CasoDeUsoAutenticacao {
    String gerarUrlAutorizacao();
    SessaoUsuario processarCallback(String codigo, String state);
    SessaoUsuario obterSessaoAtual();
    SessaoUsuario obterOuRenovarSessaoValida();
}
