package br.com.mirante.domain.tenant;

import java.util.regex.Pattern;

/**
 * Identidade visual do cliente. E o que faz a mesma base servir a marcas
 * diferentes sem uma linha de codigo por cliente.
 */
public record Marca(String nomeExibido, String logoUri, String corPrimaria, String corDeFundo) {

    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public Marca {
        if (nomeExibido == null || nomeExibido.isBlank()) {
            throw new IllegalArgumentException("nome exibido e obrigatorio");
        }
        corPrimaria = validarCor(corPrimaria, "#e6b800");
        corDeFundo = validarCor(corDeFundo, "#0d0f14");
    }

    private static String validarCor(String cor, String padrao) {
        if (cor == null || cor.isBlank()) {
            return padrao;
        }
        if (!HEX.matcher(cor).matches()) {
            throw new IllegalArgumentException("cor precisa estar em hexadecimal, ex: #1a2b3c");
        }
        return cor.toLowerCase();
    }

    public static Marca padrao(String nomeExibido) {
        return new Marca(nomeExibido, null, null, null);
    }
}
