package br.com.playlistmaker.core.dominio;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistroSincronizacaoTest {

    private static final Instant INICIO = Instant.parse("2026-11-24T07:00:00Z");
    private static final Instant FIM = Instant.parse("2026-11-24T07:05:00Z");

    @Test
    void deveCriarRegistroViaFactory() {
        RegistroSincronizacao registro = RegistroSincronizacao.iniciar(INICIO);

        assertThat(registro.getId()).isNull();
        assertThat(registro.getIniciadoEm()).isEqualTo(INICIO);
        assertThat(registro.getFinalizadoEm()).isNull();
        assertThat(registro.getStatus()).isEqualTo(StatusSincronizacao.EM_ANDAMENTO);
        assertThat(registro.getTotalCurtidas()).isZero();
        assertThat(registro.getNovasFaixas()).isZero();
        assertThat(registro.getRemovidasFaixas()).isZero();
        assertThat(registro.getMensagem()).isEqualTo("Sincronizacao iniciada");
        assertThat(registro.getTempoEsperaSegundos()).isNull();
    }

    @Test
    void deveCriarRegistroComConstrutorCompleto() {
        RegistroSincronizacao registro = new RegistroSincronizacao(
                1L, INICIO, FIM, StatusSincronizacao.CONCLUIDO, 100, 10, 2, "Concluido com sucesso", null
        );

        assertThat(registro.getId()).isEqualTo(1L);
        assertThat(registro.getIniciadoEm()).isEqualTo(INICIO);
        assertThat(registro.getFinalizadoEm()).isEqualTo(FIM);
        assertThat(registro.getStatus()).isEqualTo(StatusSincronizacao.CONCLUIDO);
        assertThat(registro.getTotalCurtidas()).isEqualTo(100);
        assertThat(registro.getNovasFaixas()).isEqualTo(10);
        assertThat(registro.getRemovidasFaixas()).isEqualTo(2);
        assertThat(registro.getMensagem()).isEqualTo("Concluido com sucesso");
        assertThat(registro.getTempoEsperaSegundos()).isNull();
    }

    @Test
    void deveRejeitarIniciadoEmNulo() {
        assertThatThrownBy(() ->
                new RegistroSincronizacao(null, null, null, StatusSincronizacao.EM_ANDAMENTO, 0, 0, 0, null, null)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("iniciadoEm");
    }

    @Test
    void deveRejeitarStatusNulo() {
        assertThatThrownBy(() ->
                new RegistroSincronizacao(null, INICIO, null, null, 0, 0, 0, null, null)
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status");
    }

    @Test
    void deveConcluirRegistro() {
        RegistroSincronizacao emAndamento = RegistroSincronizacao.iniciar(INICIO);
        RegistroSincronizacao concluido = emAndamento.concluir(FIM, 742, 15, 3, "Sincronizacao concluida com sucesso. 742 musicas no catalogo local.");

        assertThat(concluido.getStatus()).isEqualTo(StatusSincronizacao.CONCLUIDO);
        assertThat(concluido.getFinalizadoEm()).isEqualTo(FIM);
        assertThat(concluido.getTotalCurtidas()).isEqualTo(742);
        assertThat(concluido.getNovasFaixas()).isEqualTo(15);
        assertThat(concluido.getRemovidasFaixas()).isEqualTo(3);
        assertThat(concluido.getMensagem()).isEqualTo("Sincronizacao concluida com sucesso. 742 musicas no catalogo local.");
        assertThat(concluido.getTempoEsperaSegundos()).isNull();
        // preserva o inicio
        assertThat(concluido.getIniciadoEm()).isEqualTo(INICIO);
    }

    @Test
    void deveLimitarRegistro() {
        RegistroSincronizacao emAndamento = RegistroSincronizacao.iniciar(INICIO);
        RegistroSincronizacao limitado = emAndamento.limitar(FIM, 120, "Limite de requisicoes atingido. Tente novamente em 120 segundos.");

        assertThat(limitado.getStatus()).isEqualTo(StatusSincronizacao.LIMITADO);
        assertThat(limitado.getFinalizadoEm()).isEqualTo(FIM);
        assertThat(limitado.getTempoEsperaSegundos()).isEqualTo(120);
        assertThat(limitado.getMensagem()).isEqualTo("Limite de requisicoes atingido. Tente novamente em 120 segundos.");
        assertThat(limitado.getIniciadoEm()).isEqualTo(INICIO);
    }

    @Test
    void deveFalharRegistro() {
        RegistroSincronizacao emAndamento = RegistroSincronizacao.iniciar(INICIO);
        RegistroSincronizacao falho = emAndamento.falhar(FIM, "Erro de comunicacao com o Spotify.");

        assertThat(falho.getStatus()).isEqualTo(StatusSincronizacao.ERRO);
        assertThat(falho.getFinalizadoEm()).isEqualTo(FIM);
        assertThat(falho.getMensagem()).isEqualTo("Erro de comunicacao com o Spotify.");
        assertThat(falho.getTempoEsperaSegundos()).isNull();
        assertThat(falho.getIniciadoEm()).isEqualTo(INICIO);
    }

    @Test
    void deveConcluirComZeroNovasERemovidasQuandoNaoHouveMudancas() {
        RegistroSincronizacao emAndamento = RegistroSincronizacao.iniciar(INICIO);
        RegistroSincronizacao concluido = emAndamento.concluir(FIM, 100, 0, 0, "Nenhuma alteracao detectada.");

        assertThat(concluido.getNovasFaixas()).isZero();
        assertThat(concluido.getRemovidasFaixas()).isZero();
        assertThat(concluido.getTotalCurtidas()).isEqualTo(100);
    }

    @Test
    void devePreservarIdQuandoConcluir() {
        RegistroSincronizacao comId = new RegistroSincronizacao(
                42L, INICIO, null, StatusSincronizacao.EM_ANDAMENTO, 0, 0, 0, "Iniciado", null
        );
        RegistroSincronizacao concluido = comId.concluir(FIM, 10, 5, 1, "Ok");

        assertThat(concluido.getId()).isEqualTo(42L);
    }

    @Test
    void deveVerificarTodosOsStatusDoEnum() {
        assertThat(StatusSincronizacao.values()).containsExactlyInAnyOrder(
                StatusSincronizacao.EM_ANDAMENTO,
                StatusSincronizacao.CONCLUIDO,
                StatusSincronizacao.LIMITADO,
                StatusSincronizacao.ERRO
        );
    }
}
