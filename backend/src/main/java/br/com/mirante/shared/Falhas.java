package br.com.mirante.shared;

/** Falhas reaproveitadas em varios pontos do dominio. */
public final class Falhas {

    private Falhas() {}

    public static FalhaDeNegocio naoEncontrado(String recurso) {
        return new FalhaDeNegocio("NAO_ENCONTRADO", recurso + " nao encontrado");
    }

    public static FalhaDeNegocio semPermissao(String acao) {
        return new FalhaDeNegocio("SEM_PERMISSAO", "Sem permissao para " + acao);
    }

    public static FalhaDeNegocio invalido(String mensagem) {
        return new FalhaDeNegocio("DADO_INVALIDO", mensagem);
    }

    public static FalhaDeNegocio conflito(String mensagem) {
        return new FalhaDeNegocio("CONFLITO", mensagem);
    }
}
