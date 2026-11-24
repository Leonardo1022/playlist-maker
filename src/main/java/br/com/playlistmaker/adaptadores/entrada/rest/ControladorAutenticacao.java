package br.com.playlistmaker.adaptadores.entrada.rest;

import br.com.playlistmaker.adaptadores.entrada.rest.dto.RespostaCallbackAuthDTO;
import br.com.playlistmaker.adaptadores.entrada.rest.dto.RespostaLoginUrlDTO;
import br.com.playlistmaker.adaptadores.entrada.rest.dto.RespostaStatusAuthDTO;
import br.com.playlistmaker.core.dominio.SessaoUsuario;
import br.com.playlistmaker.core.porta.entrada.CasoDeUsoAutenticacao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class ControladorAutenticacao {

    private final CasoDeUsoAutenticacao casoDeUsoAutenticacao;

    public ControladorAutenticacao(CasoDeUsoAutenticacao casoDeUsoAutenticacao) {
        this.casoDeUsoAutenticacao = casoDeUsoAutenticacao;
    }

    @GetMapping("/status")
    public ResponseEntity<RespostaStatusAuthDTO> obterStatus() {
        SessaoUsuario sessao = casoDeUsoAutenticacao.obterSessaoAtual();
        boolean ativa = sessao.estaAtiva();
        String mensagem = ativa ? "Conta conectada e sessao ativa" : "Nenhuma conta do Spotify conectada ou sessao expirada";
        String conectadoEm = (sessao.getAtualizadoEm() != null) ? sessao.getAtualizadoEm().toString() : null;

        return ResponseEntity.ok(new RespostaStatusAuthDTO(
                ativa,
                sessao.getEstado().name(),
                sessao.getUsuarioId(),
                conectadoEm,
                mensagem
        ));
    }

    @GetMapping("/login-url")
    public ResponseEntity<RespostaLoginUrlDTO> obterLoginUrl() {
        String url = casoDeUsoAutenticacao.gerarUrlAutorizacao();
        return ResponseEntity.ok(new RespostaLoginUrlDTO(url));
    }

    @GetMapping("/callback")
    public ResponseEntity<RespostaCallbackAuthDTO> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {

        if (error != null && !error.isBlank()) {
            throw new IllegalArgumentException("Autorizacao cancelada ou recusada pelo usuario: " + error);
        }

        if (code == null || state == null) {
            throw new IllegalArgumentException("Parametros code e state sao obrigatorios");
        }

        casoDeUsoAutenticacao.processarCallback(code, state);
        return ResponseEntity.ok(new RespostaCallbackAuthDTO("CONECTADO", "Conta do Spotify conectada com sucesso"));
    }
}
