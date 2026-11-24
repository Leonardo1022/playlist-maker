package br.com.playlistmaker.adaptadores.entrada.rest;

import br.com.playlistmaker.adaptadores.entrada.rest.dto.RespostaErroDTO;
import br.com.playlistmaker.core.dominio.excecoes.FalhaComunicacaoException;
import br.com.playlistmaker.core.dominio.excecoes.RateLimitExcedidoException;
import br.com.playlistmaker.core.dominio.excecoes.SessaoInvalidaException;
import br.com.playlistmaker.core.dominio.excecoes.SpotifyPremiumRequeridoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TratadorGlobalExcecoes {

    @ExceptionHandler(SessaoInvalidaException.class)
    public ResponseEntity<RespostaErroDTO> tratarSessaoInvalida(SessaoInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new RespostaErroDTO("SESSAO_EXPIRADA", ex.getMessage()));
    }

    @ExceptionHandler(SpotifyPremiumRequeridoException.class)
    public ResponseEntity<RespostaErroDTO> tratarPremiumRequerido(SpotifyPremiumRequeridoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RespostaErroDTO("PREMIUM_REQUERIDO", ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExcedidoException.class)
    public ResponseEntity<RespostaErroDTO> tratarRateLimit(RateLimitExcedidoException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new RespostaErroDTO("RATE_LIMIT_EXCEDIDO", ex.getMessage(), ex.getTempoEsperaSegundos()));
    }

    @ExceptionHandler(FalhaComunicacaoException.class)
    public ResponseEntity<RespostaErroDTO> tratarFalhaComunicacao(FalhaComunicacaoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new RespostaErroDTO("ERRO_COMUNICACAO", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RespostaErroDTO> tratarIllegalState(IllegalStateException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("andamento")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new RespostaErroDTO("SINCRONIZACAO_EM_ANDAMENTO", ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RespostaErroDTO("ESTADO_INVALIDO", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<RespostaErroDTO> tratarIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RespostaErroDTO("REQUISICAO_INVALIDA", ex.getMessage()));
    }
}
