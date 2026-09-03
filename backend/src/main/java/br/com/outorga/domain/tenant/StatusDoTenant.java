package br.com.outorga.domain.tenant;

public enum StatusDoTenant {
    /** Contrato assinado, catálogo em carga, ainda sem público. */
    EM_IMPLANTACAO,
    ATIVO,
    /** Inadimplencia ou pedido do cliente. Painel abre, espectador não entra. */
    SUSPENSO,
    ENCERRADO
}
