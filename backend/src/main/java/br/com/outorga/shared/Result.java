package br.com.outorga.shared;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resultado de uma operação de negocio: ou deu certo e carrega um valor, ou
 * falhou e carrega o motivo. Casos de uso devolvem Result; a camada HTTP faz o
 * unwrap. O dominio não lanca exceção para condição esperada.
 */
public sealed interface Result<T> permits Result.Ok, Result.Erro {

    record Ok<T>(T valor) implements Result<T> {}

    record Erro<T>(FalhaDeNegocio motivo) implements Result<T> {}

    static <T> Result<T> ok(T valor) {
        return new Ok<>(valor);
    }

    static <T> Result<T> erro(FalhaDeNegocio falha) {
        return new Erro<>(falha);
    }

    static <T> Result<T> erro(String codigo, String mensagem) {
        return new Erro<>(new FalhaDeNegocio(codigo, mensagem));
    }

    default boolean sucesso() {
        return this instanceof Ok<T>;
    }

    default boolean falhou() {
        return this instanceof Erro<T>;
    }

    /** So chame depois de checar {@link #sucesso()}. */
    default T valorOuFalha() {
        return switch (this) {
            case Ok<T> ok -> ok.valor();
            case Erro<T> e -> throw new NoSuchElementException(
                    "Result em erro (" + e.motivo().codigo() + ") acessado como sucesso");
        };
    }

    default Optional<FalhaDeNegocio> falha() {
        return this instanceof Erro<T> e ? Optional.of(e.motivo()) : Optional.empty();
    }

    default T ouEntao(T alternativa) {
        return this instanceof Ok<T> ok ? ok.valor() : alternativa;
    }

    default <R> Result<R> mapear(Function<T, R> f) {
        return switch (this) {
            case Ok<T> ok -> Result.ok(f.apply(ok.valor()));
            case Erro<T> e -> Result.erro(e.motivo());
        };
    }

    default <R> Result<R> entao(Function<T, Result<R>> f) {
        return switch (this) {
            case Ok<T> ok -> f.apply(ok.valor());
            case Erro<T> e -> Result.erro(e.motivo());
        };
    }

    /** Encadeia uma verificacao que não produz valor novo. */
    default Result<T> verificar(Supplier<Optional<FalhaDeNegocio>> verificacao) {
        if (falhou()) {
            return this;
        }
        return verificacao.get().<Result<T>>map(Result::erro).orElse(this);
    }
}
