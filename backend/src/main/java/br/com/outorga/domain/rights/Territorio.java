package br.com.outorga.domain.rights;

import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.util.Locale;
import java.util.Set;

/**
 * Território de exploracao, em código ISO 3166-1 alpha-2. O valor especial
 * "WW" cobre o mundo todo.
 */
public record Territorio(String codigo) {

    // A ordem destas três linhas importa. As constantes abaixo passam pelo
    // construtor, que consulta ISO; se ISO for declarado depois delas, ele
    // ainda e nulo na hora e a classe nem carrega.
    private static final Set<String> ISO = Set.of(Locale.getISOCountries());

    public static final Territorio MUNDIAL = new Territorio("WW");
    public static final Territorio BRASIL = new Territorio("BR");

    public Territorio {
        if (codigo == null) {
            throw new IllegalArgumentException("território e obrigatório");
        }
        codigo = codigo.trim().toUpperCase(Locale.ROOT);
        if (!codigo.equals("WW") && !ISO.contains(codigo)) {
            throw new IllegalArgumentException("território inválido: " + codigo);
        }
    }

    public static Result<Territorio> de(String codigo) {
        try {
            return Result.ok(new Territorio(codigo));
        } catch (IllegalArgumentException e) {
            return Result.erro(new FalhaDeNegocio("TERRITORIO_INVALIDO", e.getMessage()));
        }
    }

    /** Mundial cobre qualquer território; os demais cobrem apenas a si mesmos. */
    public boolean cobre(Territorio outro) {
        return equals(MUNDIAL) || equals(outro);
    }
}
