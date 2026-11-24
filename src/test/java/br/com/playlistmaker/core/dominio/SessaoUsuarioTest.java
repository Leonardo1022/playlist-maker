package br.com.playlistmaker.core.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessaoUsuarioTest {

    @Test
    void deveCriarSessaoComSucesso() {
        Instant agora = Instant.now();
        Instant expira = agora.plus(1, ChronoUnit.HOURS);
        SessaoUsuario sessao = new SessaoUsuario(1L, "user123", "token123", "refresh123", expira, agora, EstadoSessao.ATIVO);

        assertThat(sessao.getId()).isEqualTo(1L);
        assertThat(sessao.getUsuarioId()).isEqualTo("user123");
        assertThat(sessao.getAccessToken()).isEqualTo("token123");
        assertThat(sessao.getRefreshToken()).isEqualTo("refresh123");
        assertThat(sessao.getExpiraEm()).isEqualTo(expira);
        assertThat(sessao.getAtualizadoEm()).isEqualTo(agora);
        assertThat(sessao.getEstado()).isEqualTo(EstadoSessao.ATIVO);
        assertThat(sessao.estaAtiva()).isTrue();
        assertThat(sessao.precisaRenovar()).isFalse();
    }

    @Test
    void deveAceitarIdNuloEAtribuirPadraoUm() {
        Instant agora = Instant.now();
        SessaoUsuario sessao = new SessaoUsuario(null, "user123", "t", "r", agora.plusSeconds(3600), agora, EstadoSessao.ATIVO);
        assertThat(sessao.getId()).isEqualTo(1L);
    }

    @Test
    void deveRejeitarIdDiferenteDeUm() {
        Instant agora = Instant.now();
        assertThatThrownBy(() -> new SessaoUsuario(2L, "user", "t", "r", agora, agora, EstadoSessao.ATIVO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("O id da sessao de usuario unico deve ser 1");
    }

    @Test
    void deveValidarCamposObrigatorios() {
        Instant agora = Instant.now();
        assertThatThrownBy(() -> new SessaoUsuario(1L, "user", null, "r", agora, agora, EstadoSessao.ATIVO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessaoUsuario(1L, "user", "t", null, agora, agora, EstadoSessao.ATIVO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessaoUsuario(1L, "user", "t", "r", null, agora, EstadoSessao.ATIVO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessaoUsuario(1L, "user", "t", "r", agora, null, EstadoSessao.ATIVO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessaoUsuario(1L, "user", "t", "r", agora, agora, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deveCriarSessaoDesconectada() {
        SessaoUsuario sessao = SessaoUsuario.desconectada();
        assertThat(sessao.getId()).isEqualTo(1L);
        assertThat(sessao.getUsuarioId()).isNull();
        assertThat(sessao.getEstado()).isEqualTo(EstadoSessao.DESCONECTADO);
        assertThat(sessao.estaAtiva()).isFalse();
        assertThat(sessao.precisaRenovar()).isFalse();
    }

    @Test
    void deveIdentificarNecessidadeDeRenovacao() {
        Instant agora = Instant.now();
        // Expira em 30 segundos -> precisa renovar (janela de 60s)
        SessaoUsuario sessao = new SessaoUsuario(1L, "user", "t", "r", agora.plusSeconds(30), agora, EstadoSessao.ATIVO);
        assertThat(sessao.estaAtiva()).isTrue();
        assertThat(sessao.precisaRenovar()).isTrue();

        // Ja expirou -> nao esta ativa
        SessaoUsuario expirada = new SessaoUsuario(1L, "user", "t", "r", agora.minusSeconds(10), agora, EstadoSessao.ATIVO);
        assertThat(expirada.estaAtiva()).isFalse();
    }

    @Test
    void deveAtualizarTokensEMarcarExpirada() {
        Instant agora = Instant.now();
        SessaoUsuario sessao = new SessaoUsuario(1L, "user", "t1", "r1", agora.plusSeconds(3600), agora, EstadoSessao.ATIVO);

        SessaoUsuario atualizada = sessao.atualizarTokens("t2", "r2", 3600);
        assertThat(atualizada.getAccessToken()).isEqualTo("t2");
        assertThat(atualizada.getRefreshToken()).isEqualTo("r2");
        assertThat(atualizada.getEstado()).isEqualTo(EstadoSessao.ATIVO);

        // Se novo refresh for vazio, mantem o antigo
        SessaoUsuario semNovoRefresh = atualizada.atualizarTokens("t3", "", 3600);
        assertThat(semNovoRefresh.getAccessToken()).isEqualTo("t3");
        assertThat(semNovoRefresh.getRefreshToken()).isEqualTo("r2");

        SessaoUsuario expirada = semNovoRefresh.marcarExpirada();
        assertThat(expirada.getEstado()).isEqualTo(EstadoSessao.EXPIRADO);
        assertThat(expirada.estaAtiva()).isFalse();
    }
}
