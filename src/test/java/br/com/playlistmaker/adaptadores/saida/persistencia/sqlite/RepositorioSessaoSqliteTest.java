package br.com.playlistmaker.adaptadores.saida.persistencia.sqlite;

import br.com.playlistmaker.core.dominio.EstadoSessao;
import br.com.playlistmaker.core.dominio.SessaoUsuario;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepositorioSessaoSqliteTest {

    private AdaptadorRepositorioSessaoSqlite repositorio;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        String dbPath = tempDir.resolve("test-sessao.db").toAbsolutePath().toString();
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        repositorio = new AdaptadorRepositorioSessaoSqlite(jdbcClient);
    }

    @Test
    void deveSalvarERecuperarSessaoComSucesso() {
        Instant agora = Instant.now();
        Instant expira = agora.plusSeconds(3600);
        SessaoUsuario sessao = new SessaoUsuario(1L, "spotify_user_99", "acc_token", "ref_token", expira, agora, EstadoSessao.ATIVO);

        repositorio.salvar(sessao);

        Optional<SessaoUsuario> recuperada = repositorio.obter();
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().getId()).isEqualTo(1L);
        assertThat(recuperada.get().getUsuarioId()).isEqualTo("spotify_user_99");
        assertThat(recuperada.get().getAccessToken()).isEqualTo("acc_token");
        assertThat(recuperada.get().getRefreshToken()).isEqualTo("ref_token");
        assertThat(recuperada.get().getEstado()).isEqualTo(EstadoSessao.ATIVO);
    }

    @Test
    void deveAtualizarSessaoExistenteIdempotentemente() {
        Instant agora = Instant.now();
        SessaoUsuario sessao1 = new SessaoUsuario(1L, "user1", "t1", "r1", agora.plusSeconds(3600), agora, EstadoSessao.ATIVO);
        repositorio.salvar(sessao1);

        SessaoUsuario sessao2 = new SessaoUsuario(1L, "user1", "t2", "r2", agora.plusSeconds(7200), agora, EstadoSessao.ATIVO);
        repositorio.salvar(sessao2);

        Optional<SessaoUsuario> recuperada = repositorio.obter();
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().getAccessToken()).isEqualTo("t2");
        assertThat(recuperada.get().getRefreshToken()).isEqualTo("r2");
    }

    @Test
    void deveRemoverSessao() {
        Instant agora = Instant.now();
        SessaoUsuario sessao = new SessaoUsuario(1L, "u", "t", "r", agora.plusSeconds(3600), agora, EstadoSessao.ATIVO);
        repositorio.salvar(sessao);
        assertThat(repositorio.obter()).isPresent();

        repositorio.remover();
        assertThat(repositorio.obter()).isEmpty();
    }
}
