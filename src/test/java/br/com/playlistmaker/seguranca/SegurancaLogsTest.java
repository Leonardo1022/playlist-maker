package br.com.playlistmaker.seguranca;

import br.com.playlistmaker.core.dominio.EstadoSessao;
import br.com.playlistmaker.core.dominio.SessaoUsuario;
import br.com.playlistmaker.core.porta.saida.PortaRepositorioSessao;
import br.com.playlistmaker.core.porta.saida.PortaSpotifyClient;
import br.com.playlistmaker.core.servico.ServicoAutenticacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Teste de seguranca que verifica que tokens (accessToken e refreshToken)
 * jamais aparecem em logs ou saidas de console durante operacoes normais
 * e de erro do sistema. (Principio II e X da Constituicao v1.2.0)
 */
@ExtendWith(OutputCaptureExtension.class)
class SegurancaLogsTest {

    private static final String ACCESS_TOKEN_SECRETO = "SECRET_ACCESS_TOKEN_MUITO_SECRETO_XYZ789";
    private static final String REFRESH_TOKEN_SECRETO = "SECRET_REFRESH_TOKEN_ULTRA_SECRETO_ABC123";

    private PortaSpotifyClient portaSpotifyClient;
    private PortaRepositorioSessao portaRepositorioSessao;
    private ServicoAutenticacao servico;

    @BeforeEach
    void setUp() {
        portaSpotifyClient = mock(PortaSpotifyClient.class);
        portaRepositorioSessao = mock(PortaRepositorioSessao.class);
        servico = new ServicoAutenticacao(
                portaSpotifyClient,
                portaRepositorioSessao,
                "test-client-id",
                "http://127.0.0.1:8888/api/auth/callback",
                "http://localhost:9999"
        );
    }

    @Test
    void deveGarantirQueAccessTokenNaoAparece_EmLogNormal(CapturedOutput output) {
        // Configurar sessao com token secreto e operacao normal (obterSessaoAtual)
        Instant agora = Instant.now();
        SessaoUsuario sessao = new SessaoUsuario(
                1L, "usuario_123",
                ACCESS_TOKEN_SECRETO, REFRESH_TOKEN_SECRETO,
                agora.plusSeconds(3600), agora, EstadoSessao.ATIVO
        );
        when(portaRepositorioSessao.obter()).thenReturn(Optional.of(sessao));

        // Executar operacao que carrega a sessao
        servico.obterSessaoAtual();

        // Verificar que os tokens nao aparecem nos logs
        String saida = output.getAll();
        assertThat(saida)
                .as("O accessToken nao deve aparecer em nenhuma saida de log ou console")
                .doesNotContain(ACCESS_TOKEN_SECRETO);
        assertThat(saida)
                .as("O refreshToken nao deve aparecer em nenhuma saida de log ou console")
                .doesNotContain(REFRESH_TOKEN_SECRETO);
    }

    @Test
    void deveGarantirQueTokensNaoAparecem_EmLogDeRenovacao(CapturedOutput output) {
        // Sessao quase expirada — ativa a renovacao automatica
        Instant agora = Instant.now();
        SessaoUsuario sessaoQuaseExpirada = new SessaoUsuario(
                1L, "usuario_123",
                ACCESS_TOKEN_SECRETO, REFRESH_TOKEN_SECRETO,
                agora.plusSeconds(30), agora, EstadoSessao.ATIVO
        );
        when(portaRepositorioSessao.obter()).thenReturn(Optional.of(sessaoQuaseExpirada));

        String novoToken = "NOVO_ACCESS_TOKEN_IGUALMENTE_SECRETO";
        String novoRefresh = "NOVO_REFRESH_TOKEN_IGUALMENTE_SECRETO";
        when(portaSpotifyClient.renovarToken(REFRESH_TOKEN_SECRETO))
                .thenReturn(new PortaSpotifyClient.TokenResultado(novoToken, novoRefresh, 3600, "user-library-read"));

        // Executar renovacao
        servico.obterOuRenovarSessaoValida();

        // Verificar que nenhum token aparece nos logs
        String saida = output.getAll();
        assertThat(saida).doesNotContain(ACCESS_TOKEN_SECRETO);
        assertThat(saida).doesNotContain(REFRESH_TOKEN_SECRETO);
        assertThat(saida).doesNotContain(novoToken);
        assertThat(saida).doesNotContain(novoRefresh);
    }

    @Test
    void deveGarantirQueTokensNaoAparecem_EmLogDeCallback(CapturedOutput output) {
        // Simular processamento de callback OAuth
        String urlAutorizacao = servico.gerarUrlAutorizacao();
        String state = extrairParametroState(urlAutorizacao);

        when(portaSpotifyClient.trocarCodigoPorToken(anyString(), anyString(), anyString()))
                .thenReturn(new PortaSpotifyClient.TokenResultado(
                        ACCESS_TOKEN_SECRETO, REFRESH_TOKEN_SECRETO, 3600, "user-library-read"));
        doNothing().when(portaSpotifyClient).verificarContaPremium(anyString());

        servico.processarCallback("auth-code-xyz", state);

        // Verificar que os tokens obtidos no callback nao aparecem em nenhuma saida
        String saida = output.getAll();
        assertThat(saida).doesNotContain(ACCESS_TOKEN_SECRETO);
        assertThat(saida).doesNotContain(REFRESH_TOKEN_SECRETO);
    }

    @Test
    void deveGarantirQueTokenNaoAparece_EmLogDeExcecaoDeRenovacao(CapturedOutput output) {
        // Simular falha de renovacao — o token invalido nao deve aparecer em logs de erro
        Instant agora = Instant.now();
        SessaoUsuario sessao = new SessaoUsuario(
                1L, "usuario_123",
                ACCESS_TOKEN_SECRETO, REFRESH_TOKEN_SECRETO,
                agora.plusSeconds(30), agora, EstadoSessao.ATIVO
        );
        when(portaRepositorioSessao.obter()).thenReturn(Optional.of(sessao));
        when(portaSpotifyClient.renovarToken(REFRESH_TOKEN_SECRETO))
                .thenThrow(new RuntimeException("Falha na renovacao"));

        // A excecao sera lancada mas os tokens nao devem aparecer em log
        assertThatThrownBy(() -> servico.obterOuRenovarSessaoValida())
                .hasMessageContaining("Sessao expirada");

        String saida = output.getAll();
        assertThat(saida).doesNotContain(ACCESS_TOKEN_SECRETO);
        assertThat(saida).doesNotContain(REFRESH_TOKEN_SECRETO);
    }

    // Utilitario para extrair o parametro state da URL de autorizacao
    private String extrairParametroState(String url) {
        String queryString = url.contains("?") ? url.split("\\?", 2)[1] : "";
        for (String par : queryString.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length == 2 && "state".equals(kv[0])) {
                return java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Parametro state nao encontrado na URL de autorizacao");
    }
}
