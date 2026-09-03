package br.com.outorga.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Erros que escapam do caminho feliz.
 *
 * Regra da casa: o cliente recebe uma mensagem útil e um código; o stack trace
 * fica no log com um identificador que aparece nos dois lados. Detalhe de
 * exceção vazado na resposta e mapa do sistema entregue de graca.
 */
@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Respostas.Erro> validacao(MethodArgumentNotValidException e) {
        var campos = new LinkedHashMap<String, Object>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.put(erro.getField(), erro.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new Respostas.Erro(
                "DADO_INVALIDO", "Confira os campos enviados", campos));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Respostas.Erro> requisicaoMalFormada(Exception e) {
        return ResponseEntity.badRequest().body(new Respostas.Erro(
                "REQUISICAO_INVALIDA", "Não foi possível ler a requisicao", Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Respostas.Erro> semPermissao(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Respostas.Erro(
                "SEM_PERMISSAO", "Sem permissão para está operação", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Respostas.Erro> inesperado(Exception e, HttpServletRequest requisicao) {
        var identificador = UUID.randomUUID().toString().substring(0, 8);
        log.error("Erro inesperado [{}] em {} {}", identificador, requisicao.getMethod(),
                requisicao.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new Respostas.Erro(
                "ERRO_INTERNO",
                "Algo deu errado do nosso lado. Guarde o código " + identificador
                        + " se precisar falar com o suporte",
                Map.of("identificador", identificador)));
    }
}
