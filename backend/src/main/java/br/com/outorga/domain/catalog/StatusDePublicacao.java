package br.com.outorga.domain.catalog;

public enum StatusDePublicacao {
    /** Em cadastro. Não aparece para ninguém fora do painel. */
    RASCUNHO,
    /** No ar. So se chega aqui com licença vigente. */
    PUBLICADO,
    /**
     * Tirado do ar pelo sistema porque a licença venceu ou foi rescindida.
     * Diferente de RASCUNHO de propósito: o operador precisa enxergar que foi
     * a licença que caiu, não alguém que despublicou.
     */
    BLOQUEADO_POR_DIREITO,
    /** Tirado do ar por decisão do operador. */
    DESPUBLICADO
}
