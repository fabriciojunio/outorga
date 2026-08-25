package br.com.mirante.domain.identity;

import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.util.Locale;
import java.util.regex.Pattern;

public record Email(String valor) {

    private static final Pattern FORMATO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        if (valor == null) {
            throw new IllegalArgumentException("email e obrigatorio");
        }
        valor = valor.trim().toLowerCase(Locale.ROOT);
        if (!FORMATO.matcher(valor).matches()) {
            throw new IllegalArgumentException("email invalido");
        }
    }

    public static Result<Email> de(String valor) {
        try {
            return Result.ok(new Email(valor));
        } catch (IllegalArgumentException e) {
            return Result.erro(new FalhaDeNegocio("EMAIL_INVALIDO", "Informe um e-mail valido"));
        }
    }

    /** Para log e tela de suporte, sem expor o endereco inteiro. */
    public String mascarado() {
        int arroba = valor.indexOf('@');
        String local = valor.substring(0, arroba);
        String visivel = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visivel + "***" + valor.substring(arroba);
    }
}
