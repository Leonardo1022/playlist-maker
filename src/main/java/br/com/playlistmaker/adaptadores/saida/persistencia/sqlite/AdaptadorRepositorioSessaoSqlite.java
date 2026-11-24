package br.com.playlistmaker.adaptadores.saida.persistencia.sqlite;

import br.com.playlistmaker.core.dominio.EstadoSessao;
import br.com.playlistmaker.core.dominio.SessaoUsuario;
import br.com.playlistmaker.core.porta.saida.PortaRepositorioSessao;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class AdaptadorRepositorioSessaoSqlite implements PortaRepositorioSessao {

    private final JdbcClient jdbcClient;

    public AdaptadorRepositorioSessaoSqlite(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void salvar(SessaoUsuario sessao) {
        String sql = """
            INSERT INTO sessao_usuario (id, usuario_id, access_token, refresh_token, expira_em, atualizado_em, estado)
            VALUES (1, :usuarioId, :accessToken, :refreshToken, :expiraEm, :atualizadoEm, :estado)
            ON CONFLICT(id) DO UPDATE SET
                usuario_id = excluded.usuario_id,
                access_token = excluded.access_token,
                refresh_token = excluded.refresh_token,
                expira_em = excluded.expira_em,
                atualizado_em = excluded.atualizado_em,
                estado = excluded.estado;
            """;

        jdbcClient.sql(sql)
                .param("usuarioId", sessao.getUsuarioId())
                .param("accessToken", sessao.getAccessToken())
                .param("refreshToken", sessao.getRefreshToken())
                .param("expiraEm", sessao.getExpiraEm().toString())
                .param("atualizadoEm", sessao.getAtualizadoEm().toString())
                .param("estado", sessao.getEstado().name())
                .update();
    }

    @Override
    public Optional<SessaoUsuario> obter() {
        String sql = "SELECT id, usuario_id, access_token, refresh_token, expira_em, atualizado_em, estado FROM sessao_usuario WHERE id = 1";

        return jdbcClient.sql(sql)
                .query((rs, rowNum) -> new SessaoUsuario(
                        rs.getLong("id"),
                        rs.getString("usuario_id"),
                        rs.getString("access_token"),
                        rs.getString("refresh_token"),
                        Instant.parse(rs.getString("expira_em")),
                        Instant.parse(rs.getString("atualizado_em")),
                        EstadoSessao.valueOf(rs.getString("estado"))
                ))
                .optional();
    }

    @Override
    public void remover() {
        jdbcClient.sql("DELETE FROM sessao_usuario WHERE id = 1").update();
    }
}
