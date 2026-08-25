package br.com.mirante.domain.catalog;

public enum StatusDePublicacao {
    /** Em cadastro. Nao aparece para ninguem fora do painel. */
    RASCUNHO,
    /** No ar. So se chega aqui com licenca vigente. */
    PUBLICADO,
    /**
     * Tirado do ar pelo sistema porque a licenca venceu ou foi rescindida.
     * Diferente de RASCUNHO de proposito: o operador precisa enxergar que foi
     * a licenca que caiu, nao alguem que despublicou.
     */
    BLOQUEADO_POR_DIREITO,
    /** Tirado do ar por decisao do operador. */
    DESPUBLICADO
}
