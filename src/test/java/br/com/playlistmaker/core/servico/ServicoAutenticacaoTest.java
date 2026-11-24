package br.com.playlistmaker.core.servico;

import br.com.playlistmaker.core.dominio.EstadoSessao;
import br.com.playlistmaker.core.dominio.SessaoUsuario;
import br.com.playlistmaker.core.dominio.excecoes.SessaoInvalidaException;
import br.com.playlistmaker.core.porta.saida.PortaRepositorioSessao;
import br.com.playlistmaker.core.porta.saida.PortaSpotifyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ServicoAutenticacaoTest {

    private PortaSpotifyClient portaSpotifyClient;
    private PortaRepositorioSessao portaRepositorioSessao;
    private ServicoAutenticacao servico;

    private final String clientId = "meu-client-id";
    private final String redirectUri = "http://127.0.0.1:8888/api/auth/callback";
    private final String accountsUrl = "https://accounts.spotify.com";

    @BeforeEach
    void setUp() {
        portaSpotifyClient = mock(PortaSpotifyClient.class);
        portaRepositorioSessao = mock(PortaRepositorioSessao.class);
        servico = new ServicoAutenticacao(portaSpotifyClient, portaRepositorioSessao, clientId, redirectUri, accountsUrl);
    }

    @Test
    void deveGerarUrlAutorizacaoComPKCEValido() {
        String url = servico.gerarUrlAutorizacao();

        assertThat(url).startsWith("https://accounts.spotify.com/authorize?");
        assertThat(url).contains("client_id=meu-client-id");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("code_challenge_method=S256");
        assertThat(url).contains("code_challenge=");
        assertThat(url).contains("state=");
        assertThat(url).contains("user-library-read+playlist-modify-private");
    }

    @Test
    void deveProcessarCallbackComSucesso() {
        String url = servico.gerarUrlAutorizacao();
        String state = extrairParametro(url, "state");

        when(portaSpotifyClient.trocarCodigoPorToken(eq("auth-code"), anyString(), eq(redirectUri)))
                .thenReturn(new PortaSpotifyClient.TokenResultado("token1", "refresh1", 3600, "user-library-read"));

        SessaoUsuario sessao = servico.processarCallback("auth-code", state);

        assertThat(sessao.getAccessToken()).isEqualTo("token1");
        assertThat(sessao.getRefreshToken()).isEqualTo("refresh1");
        assertThat(sessao.getEstado()).isEqualTo(EstadoSessao.ATIVO);

        verify(portaSpotifyClient).verificarContaPremium("token1");
        verify(portaRepositorioSessao).salvar(any(SessaoUsuario.class));
    }

    @Test
    void deveRejeitarCallbackComStateInvalidoOuAusente() {
        assertThatThrownBy(() -> servico.processarCallback("code", "state-inexistente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parametro state invalido ou expirado.");

        assertThatThrownBy(() -> servico.processarCallback("code", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveObterSessaoAtualOuDesconectada() {
        when(portaRepositorioSessao.obter()).thenReturn(Optional.empty());
        SessaoUsuario sessao = servico.obterSessaoAtual();
        assertThat(sessao.getEstado()).isEqualTo(EstadoSessao.DESCONECTADO);

        Instant agora = Instant.now();
        SessaoUsuario salva = new SessaoUsuario(1L, "user1", "t", "r", agora.plusSeconds(3600), agora, EstadoSessao.ATIVO);
        when(portaRepositorioSessao.obter()).thenReturn(Optional.of(salva));
        assertThat(servico.obterSessaoAtual()).isEqualTo(salva);
    }

    @Test
    void deveRenovarSessaoQuandoNecessario() {
        Instant agora = Instant.now();
        // Expira em 30 segundos (janela de renovacao ativada)
        SessaoUsuario sessaoQuaseExpirada = new SessaoUsuario(1L, "user1", "token-antigo", "refresh-antigo", agora.plusSeconds(30), agora, EstadoSessao.ATIVO);
        when(portaRepositorioSessao.obter()).thenReturn(Optional.of(sessaoQuaseExpirada));

        when(portaSpotifyClient.renovarToken("refresh-antigo"))
                .thenReturn(new PortaSpotifyClient.TokenResultado("token-novo", "refresh-novo", 3600, "scope"));

        SessaoUsuario sessaoValida = servico.obterOuRenovarSessaoValida();

        assertThat(sessaoValida.getAccessToken()).isEqualTo("token-novo");
        assertThat(sessaoValida.getRefreshToken()).isEqualTo("refresh-novo");
        verify(portaRepositorioSessao).salvar(any(SessaoUsuario.class));
    }

    @Test
    void deveMarcarSessaoComoExpiradaQuandoRenovacaoFalhar() {
        Instant agora = Instant.now();
        SessaoUsuario sessaoQuaseExpirada = new SessaoUsuario(1L, "user1", "token-antigo", "refresh-antigo", agora.plusSeconds(30), agora, EstadoSessao.ATIVO);
        when(portaRepositorioSessao.obter()).thenReturn(Optional.of(sessaoQuaseExpirada));

        when(portaSpotifyClient.renovarToken(anyString())).thenThrow(new RuntimeException("Revogado"));

        assertThatThrownBy(() -> servico.obterOuRenovarSessaoValida())
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessageContaining("Sessao expirada. Faca login de novo.");

        ArgumentCaptor<SessaoUsuario> captor = ArgumentCaptor.forClass(SessaoUsuario.class);
        verify(portaRepositorioSessao).salvar(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoSessao.EXPIRADO);
    }

    @Test
    void deveLancarExcecaoSeSessaoForDesconectadaOuExpirada() {
        when(portaRepositorioSessao.obter()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servico.obterOuRenovarSessaoValida())
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessageContaining("Nenhuma conta do Spotify conectada.");

        Instant agora = Instant.now();
        SessaoUsuario expirada = new SessaoUsuario(1L, "u", "t", "r", agora.minusSeconds(10), agora, EstadoSessao.EXPIRADO);
        when(portaRepositorioSessao.obter()).thenReturn(Optional.of(expirada));
        assertThatThrownBy(() -> servico.obterOuRenovarSessaoValida())
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessageContaining("Sessao expirada. Faca login de novo.");
    }

    private String extrairParametro(String url, String param) {
        String[] partes = url.split("\\?");
        if (partes.length < 2) return null;
        for (String par : partes[1].split("&")) {
            String[] chaveValor = par.split("=");
            if (chaveValor[0].equals(param)) {
                return URLDecoder.decode(chaveValor[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
