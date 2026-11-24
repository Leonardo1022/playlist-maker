package br.com.playlistmaker.adaptadores.saida.spotify;

import br.com.playlistmaker.core.dominio.excecoes.FalhaComunicacaoException;
import br.com.playlistmaker.core.dominio.excecoes.RateLimitExcedidoException;
import br.com.playlistmaker.core.dominio.excecoes.SessaoInvalidaException;
import br.com.playlistmaker.core.dominio.excecoes.SpotifyPremiumRequeridoException;
import br.com.playlistmaker.core.porta.saida.PortaSpotifyClient;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptadorSpotifyClientWireMockTest {

    private WireMockServer wireMockServer;
    private AdaptadorSpotifyClient adaptador;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        String baseUrl = "http://localhost:" + wireMockServer.port();

        RestClient apiClient = RestClient.builder().baseUrl(baseUrl).build();
        RestClient accountsClient = RestClient.builder().baseUrl(baseUrl).build();

        adaptador = new AdaptadorSpotifyClient(apiClient, accountsClient, "test-client-id");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    // ================================================================
    // Testes de troca de codigo por token (endpoint /api/token)
    // ================================================================

    @Test
    void deveTrocarCodigoPorTokenComSucesso() {
        wireMockServer.stubFor(post(urlEqualTo("/api/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("spotify/token-response.json")));

        PortaSpotifyClient.TokenResultado resultado =
                adaptador.trocarCodigoPorToken("auth-code-123", "verifier-abc", "http://127.0.0.1:8888/api/auth/callback");

        assertThat(resultado.accessToken()).isEqualTo("mock-access-token-12345");
        assertThat(resultado.refreshToken()).isEqualTo("mock-refresh-token-67890");
        assertThat(resultado.expiresInSeconds()).isEqualTo(3600L);
        assertThat(resultado.scope()).isEqualTo("user-library-read playlist-modify-private");

        wireMockServer.verify(postRequestedFor(urlEqualTo("/api/token"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .withRequestBody(containing("client_id=test-client-id")));
    }

    @Test
    void deveLancarFalhaComunicacaoQuandoTokenEndpointRetorna500() {
        wireMockServer.stubFor(post(urlEqualTo("/api/token"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"internal_server_error\"}")));

        assertThatThrownBy(() ->
                adaptador.trocarCodigoPorToken("code", "verifier", "http://127.0.0.1:8888/api/auth/callback"))
                .isInstanceOf(FalhaComunicacaoException.class);
    }

    // ================================================================
    // Testes de verificacao de conta Premium (/v1/me)
    // ================================================================

    @Test
    void deveVerificarContaPremiumComSucesso() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"user123\",\"display_name\":\"Test User\",\"product\":\"premium\"}")));

        // Deve executar sem lancamento de excecao
        adaptador.verificarContaPremium("valid-access-token");

        wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/me"))
                .withHeader("Authorization", equalTo("Bearer valid-access-token")));
    }

    @Test
    void deveLancarSpotifyPremiumRequeridoQuandoContaNaoEhPremium() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"user123\",\"display_name\":\"Free User\",\"product\":\"free\"}")));

        assertThatThrownBy(() -> adaptador.verificarContaPremium("free-user-token"))
                .isInstanceOf(SpotifyPremiumRequeridoException.class)
                .hasMessageContaining("free");
    }

    @Test
    void deveLancarSpotifyPremiumRequeridoQuandoRetorna403() {
        wireMockServer.stubFor(get(urlEqualTo("/v1/me"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"status\":403,\"message\":\"Forbidden\"}}")));

        assertThatThrownBy(() -> adaptador.verificarContaPremium("restricted-token"))
                .isInstanceOf(SpotifyPremiumRequeridoException.class);
    }

    // ================================================================
    // Testes de busca de faixas curtidas com paginacao (/v1/me/tracks)
    // ================================================================

    @Test
    void deveBuscarFaixasCurtidasPaginaUm() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/me/tracks"))
                .withQueryParam("offset", equalTo("0"))
                .withQueryParam("limit", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("spotify/saved-tracks-page1.json")));

        PortaSpotifyClient.RespostaPaginadaFaixas resposta =
                adaptador.buscarFaixasCurtidas("test-token", 0, 2);

        assertThat(resposta.total()).isEqualTo(3);
        assertThat(resposta.offset()).isEqualTo(0);
        assertThat(resposta.faixas()).hasSize(2);
        assertThat(resposta.faixas().get(0).getId()).isEqualTo("track-id-001");
        assertThat(resposta.faixas().get(0).getTitulo()).isEqualTo("Starlight");
        assertThat(resposta.faixas().get(0).getArtistas()).isEqualTo("Muse");
        assertThat(resposta.faixas().get(1).getId()).isEqualTo("track-id-002");
    }

    @Test
    void deveBuscarFaixasCurtidasPaginaDois() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/me/tracks"))
                .withQueryParam("offset", equalTo("2"))
                .withQueryParam("limit", equalTo("2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("spotify/saved-tracks-page2.json")));

        PortaSpotifyClient.RespostaPaginadaFaixas resposta =
                adaptador.buscarFaixasCurtidas("test-token", 2, 2);

        assertThat(resposta.total()).isEqualTo(3);
        assertThat(resposta.faixas()).hasSize(1);
        assertThat(resposta.faixas().get(0).getId()).isEqualTo("track-id-003");
        assertThat(resposta.faixas().get(0).getTitulo()).isEqualTo("Knights of Cydonia");
    }

    @Test
    void deveDescartarArquivosLocaisComIsLocalTrue() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/me/tracks"))
                .withQueryParam("offset", equalTo("0"))
                .withQueryParam("limit", equalTo("3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("spotify/saved-tracks-com-arquivos-locais.json")));

        // A fixture contem 1 track valida, 1 com id=null e is_local=true, 1 com is_local=true
        PortaSpotifyClient.RespostaPaginadaFaixas resposta =
                adaptador.buscarFaixasCurtidas("test-token", 0, 3);

        // Apenas a faixa com id valido e is_local=false deve ser retornada
        assertThat(resposta.faixas()).hasSize(1);
        assertThat(resposta.faixas().get(0).getId()).isEqualTo("track-id-001");
        assertThat(resposta.faixas().get(0).getFonte()).isEqualTo("spotify");
    }

    @Test
    void deveLancarSessaoInvalidaQuandoTokenExpira401() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/me/tracks"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":{\"status\":401,\"message\":\"Unauthorized\"}}")));

        assertThatThrownBy(() -> adaptador.buscarFaixasCurtidas("expired-token", 0, 50))
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessageContaining("Token de acesso expirado ou invalido.");
    }

    @Test
    void deveLancarRateLimitExcedidoComRetryAfterHeader() {
        // Rate limit com espera longa (> 60s) para garantir que a excecao seja relancada
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/me/tracks"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Retry-After", "120")
                        .withBodyFile("spotify/rate-limit-429.json")));

        assertThatThrownBy(() -> adaptador.buscarFaixasCurtidas("test-token", 0, 50))
                .isInstanceOf(RateLimitExcedidoException.class)
                .satisfies(ex -> {
                    RateLimitExcedidoException rl = (RateLimitExcedidoException) ex;
                    assertThat(rl.getTempoEsperaSegundos()).isEqualTo(120);
                });
    }
}
