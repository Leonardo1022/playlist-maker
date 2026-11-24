package br.com.playlistmaker.core.servico;

import br.com.playlistmaker.core.dominio.EstadoSessao;
import br.com.playlistmaker.core.dominio.SessaoUsuario;
import br.com.playlistmaker.core.dominio.excecoes.SessaoInvalidaException;
import br.com.playlistmaker.core.porta.entrada.CasoDeUsoAutenticacao;
import br.com.playlistmaker.core.porta.saida.PortaRepositorioSessao;
import br.com.playlistmaker.core.porta.saida.PortaSpotifyClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ServicoAutenticacao implements CasoDeUsoAutenticacao {

    private final PortaSpotifyClient portaSpotifyClient;
    private final PortaRepositorioSessao portaRepositorioSessao;
    private final String clientId;
    private final String redirectUri;
    private final String accountsUrl;

    private final Map<String, String> verifiersPorState = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public ServicoAutenticacao(
            PortaSpotifyClient portaSpotifyClient,
            PortaRepositorioSessao portaRepositorioSessao,
            @Value("${spotify.client-id}") String clientId,
            @Value("${spotify.redirect-uri}") String redirectUri,
            @Value("${spotify.accounts-url}") String accountsUrl) {
        this.portaSpotifyClient = portaSpotifyClient;
        this.portaRepositorioSessao = portaRepositorioSessao;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.accountsUrl = accountsUrl;
    }

    @Override
    public String gerarUrlAutorizacao() {
        String codeVerifier = gerarCodeVerifier();
        String codeChallenge = gerarCodeChallenge(codeVerifier);
        String state = gerarState();

        verifiersPorState.put(state, codeVerifier);

        String escopos = "user-library-read playlist-modify-private";

        return String.format("%s/authorize?client_id=%s&response_type=code&redirect_uri=%s&code_challenge_method=S256&code_challenge=%s&state=%s&scope=%s",
                accountsUrl,
                urlEncode(clientId),
                urlEncode(redirectUri),
                urlEncode(codeChallenge),
                urlEncode(state),
                urlEncode(escopos));
    }

    @Override
    public SessaoUsuario processarCallback(String codigo, String state) {
        if (state == null || !verifiersPorState.containsKey(state)) {
            throw new IllegalArgumentException("Parametro state invalido ou expirado.");
        }

        String verifier = verifiersPorState.remove(state);

        PortaSpotifyClient.TokenResultado resultado = portaSpotifyClient.trocarCodigoPorToken(codigo, verifier, redirectUri);

        // Validar se conta tem plano Premium ativo conforme Principio I e FR-017
        portaSpotifyClient.verificarContaPremium(resultado.accessToken());

        Instant agora = Instant.now();
        SessaoUsuario sessao = new SessaoUsuario(
                1L,
                null,
                resultado.accessToken(),
                resultado.refreshToken(),
                agora.plusSeconds(resultado.expiresInSeconds()),
                agora,
                EstadoSessao.ATIVO
        );

        portaRepositorioSessao.salvar(sessao);
        return sessao;
    }

    @Override
    public SessaoUsuario obterSessaoAtual() {
        return portaRepositorioSessao.obter().orElseGet(SessaoUsuario::desconectada);
    }

    @Override
    public SessaoUsuario obterOuRenovarSessaoValida() {
        SessaoUsuario sessao = obterSessaoAtual();

        if (sessao.getEstado() == EstadoSessao.DESCONECTADO) {
            throw new SessaoInvalidaException("Nenhuma conta do Spotify conectada.");
        }

        if (sessao.getEstado() == EstadoSessao.EXPIRADO || sessao.getEstado() == EstadoSessao.REVOGADO) {
            throw new SessaoInvalidaException("Sessao expirada. Faca login de novo.");
        }

        if (sessao.precisaRenovar()) {
            try {
                PortaSpotifyClient.TokenResultado novoToken = portaSpotifyClient.renovarToken(sessao.getRefreshToken());
                SessaoUsuario atualizada = sessao.atualizarTokens(
                        novoToken.accessToken(),
                        novoToken.refreshToken(),
                        novoToken.expiresInSeconds()
                );
                portaRepositorioSessao.salvar(atualizada);
                return atualizada;
            } catch (Exception ex) {
                SessaoUsuario expirada = sessao.marcarExpirada();
                portaRepositorioSessao.salvar(expirada);
                throw new SessaoInvalidaException("Sessao expirada. Faca login de novo.");
            }
        }

        return sessao;
    }

    private String gerarCodeVerifier() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String gerarCodeChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponivel", e);
        }
    }

    private String gerarState() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String urlEncode(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
