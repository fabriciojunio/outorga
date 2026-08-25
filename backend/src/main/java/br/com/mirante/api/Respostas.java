package br.com.mirante.api;

import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.function.Function;

/**
 * Traducao entre o Result do dominio e a resposta HTTP.
 *
 * O mapa abaixo e a unica fonte da verdade do status de cada falha. Deixar
 * cada controller escolher o proprio codigo e como se acaba com o mesmo erro
 * saindo 400 num endereco e 422 no outro, e cliente nenhum consegue tratar.
 */
public final class Respostas {

    private Respostas() {}

    private static final Map<String, HttpStatus> STATUS_POR_CODIGO = Map.ofEntries(
            Map.entry("NAO_ENCONTRADO", HttpStatus.NOT_FOUND),
            Map.entry("TITULO_NAO_ENCONTRADO", HttpStatus.NOT_FOUND),
            Map.entry("SERVICO_NAO_ENCONTRADO", HttpStatus.NOT_FOUND),
            Map.entry("SEM_PERMISSAO", HttpStatus.FORBIDDEN),
            Map.entry("CREDENCIAL_INVALIDA", HttpStatus.UNAUTHORIZED),
            Map.entry("TOKEN_INVALIDO", HttpStatus.UNAUTHORIZED),
            Map.entry("TOKEN_REAPROVEITADO", HttpStatus.UNAUTHORIZED),
            Map.entry("TOKEN_DE_TIPO_ERRADO", HttpStatus.UNAUTHORIZED),
            Map.entry("CONTA_INATIVA", HttpStatus.FORBIDDEN),
            Map.entry("CONTA_BLOQUEADA", HttpStatus.TOO_MANY_REQUESTS),
            Map.entry("CONFLITO", HttpStatus.CONFLICT),
            Map.entry("JA_ASSINA", HttpStatus.CONFLICT),
            Map.entry("WEBHOOK_NAO_AUTENTICO", HttpStatus.UNAUTHORIZED),
            Map.entry("GATEWAY_INDISPONIVEL", HttpStatus.BAD_GATEWAY),
            Map.entry("GATEWAY_SEM_RESPOSTA", HttpStatus.BAD_GATEWAY),
            Map.entry("SERVICO_INDISPONIVEL", HttpStatus.SERVICE_UNAVAILABLE),
            // As recusas de reproducao merecem 403 e nao 400: o pedido estava
            // certo, o que faltou foi direito de ver aquilo.
            Map.entry("ASSINATURA_SEM_ACESSO", HttpStatus.PAYMENT_REQUIRED),
            Map.entry("SEM_ASSINATURA", HttpStatus.PAYMENT_REQUIRED),
            Map.entry("LICENCA_NAO_VIGENTE", HttpStatus.FORBIDDEN),
            Map.entry("FORA_DO_TERRITORIO", HttpStatus.FORBIDDEN),
            Map.entry("DISPOSITIVO_NAO_LICENCIADO", HttpStatus.FORBIDDEN),
            Map.entry("BLOQUEADO_PELO_CONTROLE_PARENTAL", HttpStatus.FORBIDDEN),
            Map.entry("TITULO_FORA_DO_AR", HttpStatus.FORBIDDEN),
            Map.entry("TITULO_DE_OUTRO_TENANT", HttpStatus.NOT_FOUND),
            Map.entry("LIMITE_DE_TELAS", HttpStatus.CONFLICT),
            Map.entry("LIMITE_DE_DISPOSITIVOS", HttpStatus.CONFLICT),
            Map.entry("VIDEO_INDISPONIVEL", HttpStatus.SERVICE_UNAVAILABLE));

    /** Corpo de erro. Sempre com codigo estavel, para o cliente ramificar. */
    public record Erro(String codigo, String mensagem, Map<String, Object> detalhes) {

        static Erro de(FalhaDeNegocio falha) {
            return new Erro(falha.codigo(), falha.mensagem(), falha.detalhes());
        }
    }

    public static <T> ResponseEntity<?> de(Result<T> resultado) {
        return de(resultado, Function.identity());
    }

    public static <T, R> ResponseEntity<?> de(Result<T> resultado, Function<T, R> paraCorpo) {
        if (resultado.sucesso()) {
            return ResponseEntity.ok(paraCorpo.apply(resultado.valorOuFalha()));
        }
        var falha = resultado.falha().orElseThrow();
        return ResponseEntity.status(statusDe(falha)).body(Erro.de(falha));
    }

    public static <T, R> ResponseEntity<?> criado(Result<T> resultado, Function<T, R> paraCorpo) {
        if (resultado.sucesso()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(paraCorpo.apply(resultado.valorOuFalha()));
        }
        var falha = resultado.falha().orElseThrow();
        return ResponseEntity.status(statusDe(falha)).body(Erro.de(falha));
    }

    public static HttpStatus statusDe(FalhaDeNegocio falha) {
        return STATUS_POR_CODIGO.getOrDefault(falha.codigo(), HttpStatus.BAD_REQUEST);
    }
}
