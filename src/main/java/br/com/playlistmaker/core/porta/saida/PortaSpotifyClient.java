package br.com.playlistmaker.core.porta.saida;

import br.com.playlistmaker.core.dominio.FaixaCurtida;
import java.util.List;

public interface PortaSpotifyClient {
    record TokenResultado(String accessToken, String refreshToken, long expiresInSeconds, String scope) {}
    record RespostaPaginadaFaixas(int total, int offset, int limit, List<FaixaCurtida> faixas) {}

    TokenResultado trocarCodigoPorToken(String codigo, String codeVerifier, String redirectUri);
    TokenResultado renovarToken(String refreshToken);
    void verificarContaPremium(String accessToken);
    RespostaPaginadaFaixas buscarFaixasCurtidas(String accessToken, int offset, int limit);
}
