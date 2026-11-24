package br.com.playlistmaker.core.dominio;

import java.time.Instant;
import java.util.Objects;

public class SessaoUsuario {
    private final Long id;
    private final String usuarioId;
    private final String accessToken;
    private final String refreshToken;
    private final Instant expiraEm;
    private final Instant atualizadoEm;
    private final EstadoSessao estado;

    public SessaoUsuario(Long id, String usuarioId, String accessToken, String refreshToken,
                         Instant expiraEm, Instant atualizadoEm, EstadoSessao estado) {
        if (id != null && id != 1L) {
            throw new IllegalArgumentException("O id da sessao de usuario unico deve ser 1");
        }
        this.id = (id == null) ? 1L : id;
        this.usuarioId = usuarioId;
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken nao pode ser nulo");
        this.refreshToken = Objects.requireNonNull(refreshToken, "refreshToken nao pode ser nulo");
        this.expiraEm = Objects.requireNonNull(expiraEm, "expiraEm nao pode ser nulo");
        this.atualizadoEm = Objects.requireNonNull(atualizadoEm, "atualizadoEm nao pode ser nulo");
        this.estado = Objects.requireNonNull(estado, "estado nao pode ser nulo");
    }

    public static SessaoUsuario desconectada() {
        return new SessaoUsuario(1L, null, "", "", Instant.EPOCH, Instant.EPOCH, EstadoSessao.DESCONECTADO);
    }

    public boolean estaAtiva() {
        return estado == EstadoSessao.ATIVO && Instant.now().isBefore(expiraEm);
    }

    public boolean precisaRenovar() {
        return estado == EstadoSessao.ATIVO && Instant.now().plusSeconds(60).isAfter(expiraEm);
    }

    public SessaoUsuario atualizarTokens(String novoAccessToken, String novoRefreshToken, long segundosValidade) {
        Instant agora = Instant.now();
        String refresh = (novoRefreshToken != null && !novoRefreshToken.isBlank()) ? novoRefreshToken : this.refreshToken;
        return new SessaoUsuario(1L, this.usuarioId, novoAccessToken, refresh, agora.plusSeconds(segundosValidade), agora, EstadoSessao.ATIVO);
    }

    public SessaoUsuario marcarExpirada() {
        return new SessaoUsuario(1L, this.usuarioId, this.accessToken, this.refreshToken, this.expiraEm, Instant.now(), EstadoSessao.EXPIRADO);
    }

    public Long getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public EstadoSessao getEstado() { return estado; }
}
