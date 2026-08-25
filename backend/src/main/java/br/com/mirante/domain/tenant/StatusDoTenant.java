package br.com.mirante.domain.tenant;

public enum StatusDoTenant {
    /** Contrato assinado, catalogo em carga, ainda sem publico. */
    EM_IMPLANTACAO,
    ATIVO,
    /** Inadimplencia ou pedido do cliente. Painel abre, espectador nao entra. */
    SUSPENSO,
    ENCERRADO
}
