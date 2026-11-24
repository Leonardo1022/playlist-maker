package br.com.playlistmaker.adaptadores.saida.spotify;

import br.com.playlistmaker.adaptadores.saida.spotify.dto.SpotifyArtistDTO;
import br.com.playlistmaker.adaptadores.saida.spotify.dto.SpotifySavedTrackItemDTO;
import br.com.playlistmaker.adaptadores.saida.spotify.dto.SpotifySavedTracksResponseDTO;
import br.com.playlistmaker.adaptadores.saida.spotify.dto.SpotifyTokenResponseDTO;
import br.com.playlistmaker.adaptadores.saida.spotify.dto.SpotifyTrackDTO;
import br.com.playlistmaker.adaptadores.saida.spotify.dto.SpotifyUserProfileDTO;
import br.com.playlistmaker.core.dominio.FaixaCurtida;
import br.com.playlistmaker.core.dominio.excecoes.FalhaComunicacaoException;
import br.com.playlistmaker.core.dominio.excecoes.RateLimitExcedidoException;
import br.com.playlistmaker.core.dominio.excecoes.SessaoInvalidaException;
import br.com.playlistmaker.core.dominio.excecoes.SpotifyPremiumRequeridoException;
import br.com.playlistmaker.core.porta.saida.PortaSpotifyClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AdaptadorSpotifyClient implements PortaSpotifyClient {

    private final RestClient apiRestClient;
    private final RestClient accountsRestClient;
    private final String clientId;

    public AdaptadorSpotifyClient(
            @Qualifier("spotifyApiRestClient") RestClient apiRestClient,
            @Qualifier("spotifyAccountsRestClient") RestClient accountsRestClient,
            @Value("${spotify.client-id}") String clientId) {
        this.apiRestClient = apiRestClient;
        this.accountsRestClient = accountsRestClient;
        this.clientId = clientId;
    }

    @Override
    public TokenResultado trocarCodigoPorToken(String codigo, String codeVerifier, String redirectUri) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", clientId);
        formData.add("code", codigo);
        formData.add("redirect_uri", redirectUri);
        formData.add("code_verifier", codeVerifier);

        try {
            SpotifyTokenResponseDTO response = accountsRestClient.post()
                    .uri("/api/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new FalhaComunicacaoException("Erro ao trocar codigo por token: status " + res.getStatusCode());
                    })
                    .body(SpotifyTokenResponseDTO.class);

            if (response == null || response.accessToken() == null) {
                throw new FalhaComunicacaoException("Resposta de token invalida do Spotify");
            }

            long expiresIn = (response.expiresIn() != null) ? response.expiresIn() : 3600L;
            return new TokenResultado(response.accessToken(), response.refreshToken(), expiresIn, response.scope());
        } catch (FalhaComunicacaoException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FalhaComunicacaoException("Falha na chamada ao Spotify accounts: " + ex.getMessage(), ex);
        }
    }

    @Override
    public TokenResultado renovarToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        formData.add("client_id", clientId);

        try {
            SpotifyTokenResponseDTO response = accountsRestClient.post()
                    .uri("/api/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .onStatus(status -> status.value() == 400 || status.value() == 401, (req, res) -> {
                        throw new SessaoInvalidaException("Refresh token expirado ou revogado pelo usuario");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new FalhaComunicacaoException("Erro ao renovar token: status " + res.getStatusCode());
                    })
                    .body(SpotifyTokenResponseDTO.class);

            if (response == null || response.accessToken() == null) {
                throw new SessaoInvalidaException("Falha ao obter novo token");
            }

            long expiresIn = (response.expiresIn() != null) ? response.expiresIn() : 3600L;
            String novoRefresh = (response.refreshToken() != null && !response.refreshToken().isBlank())
                    ? response.refreshToken() : refreshToken;

            return new TokenResultado(response.accessToken(), novoRefresh, expiresIn, response.scope());
        } catch (SessaoInvalidaException | FalhaComunicacaoException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FalhaComunicacaoException("Falha ao renovar token com o Spotify: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void verificarContaPremium(String accessToken) {
        try {
            SpotifyUserProfileDTO profile = apiRestClient.get()
                    .uri("/v1/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.value() == 403, (req, res) -> {
                        throw new SpotifyPremiumRequeridoException(
                                "Sua conta do Spotify nao possui plano Premium ativo ou possui restricoes de modo de desenvolvimento.");
                    })
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new FalhaComunicacaoException("Erro ao consultar perfil do usuario no Spotify");
                    })
                    .body(SpotifyUserProfileDTO.class);

            if (profile != null && profile.product() != null && !profile.product().equalsIgnoreCase("premium")) {
                throw new SpotifyPremiumRequeridoException(
                        "Sua conta do Spotify possui plano '" + profile.product() + "'. A aplicacao opera em modo de desenvolvimento exigindo conta Premium ativa.");
            }
        } catch (SpotifyPremiumRequeridoException | FalhaComunicacaoException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FalhaComunicacaoException("Erro ao validar conta no Spotify: " + ex.getMessage(), ex);
        }
    }

    @Override
    public RespostaPaginadaFaixas buscarFaixasCurtidas(String accessToken, int offset, int limit) {
        int tentativas = 0;
        int maxTentativas = 3;

        while (true) {
            tentativas++;
            try {
                SpotifySavedTracksResponseDTO response = apiRestClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/v1/me/tracks")
                                .queryParam("offset", offset)
                                .queryParam("limit", limit)
                                .build())
                        .header("Authorization", "Bearer " + accessToken)
                        .retrieve()
                        .onStatus(status -> status.value() == 401, (req, res) -> {
                            throw new SessaoInvalidaException("Token de acesso expirado ou invalido.");
                        })
                        .onStatus(status -> status.value() == 429, (req, res) -> {
                            String retryAfterHeader = res.getHeaders().getFirst("Retry-After");
                            int espera = 5;
                            if (retryAfterHeader != null) {
                                try {
                                    espera = Integer.parseInt(retryAfterHeader);
                                } catch (NumberFormatException ignored) {}
                            }
                            throw new RateLimitExcedidoException("Limite de requisicoes atingido no Spotify", espera);
                        })
                        .onStatus(HttpStatusCode::isError, (req, res) -> {
                            throw new FalhaComunicacaoException("Erro ao consultar musicas curtidas no Spotify: " + res.getStatusCode());
                        })
                        .body(SpotifySavedTracksResponseDTO.class);

                if (response == null || response.items() == null) {
                    return new RespostaPaginadaFaixas(0, offset, limit, List.of());
                }

                Instant agora = Instant.now();
                List<FaixaCurtida> faixasValidas = new ArrayList<>();

                for (SpotifySavedTrackItemDTO item : response.items()) {
                    if (item.track() == null) continue;
                    SpotifyTrackDTO t = item.track();

                    // Ignorar arquivos locais sem identificador oficial (FR-007)
                    if (t.id() == null || t.id().isBlank() || Boolean.TRUE.equals(t.isLocal())) {
                        continue;
                    }

                    String titulo = (t.name() != null) ? t.name() : "Desconhecido";
                    String artistas = "Desconhecido";
                    if (t.artists() != null && !t.artists().isEmpty()) {
                        artistas = t.artists().stream()
                                .map(SpotifyArtistDTO::name)
                                .filter(n -> n != null && !n.isBlank())
                                .collect(Collectors.joining(", "));
                    }
                    String album = (t.album() != null && t.album().name() != null) ? t.album().name() : "Desconhecido";
                    long duracao = (t.durationMs() != null && t.durationMs() > 0) ? t.durationMs() : 1L;

                    Instant curtidaEm;
                    try {
                        curtidaEm = (item.addedAt() != null) ? Instant.parse(item.addedAt()) : agora;
                    } catch (Exception e) {
                        curtidaEm = agora;
                    }

                    faixasValidas.add(new FaixaCurtida(t.id(), titulo, artistas, album, duracao, curtidaEm, agora, "spotify"));
                }

                int total = (response.total() != null) ? response.total() : faixasValidas.size();
                return new RespostaPaginadaFaixas(total, offset, limit, faixasValidas);

            } catch (RateLimitExcedidoException ex) {
                if (ex.getTempoEsperaSegundos() <= 60 && tentativas < maxTentativas) {
                    try {
                        Thread.sleep(ex.getTempoEsperaSegundos() * 1000L);
                        continue;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new FalhaComunicacaoException("Interrupcao durante espera de Rate Limit", ie);
                    }
                }
                throw ex;
            } catch (SessaoInvalidaException ex) {
                throw ex;
            } catch (FalhaComunicacaoException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new FalhaComunicacaoException("Falha na requisicao ao Spotify: " + ex.getMessage(), ex);
            }
        }
    }
}
