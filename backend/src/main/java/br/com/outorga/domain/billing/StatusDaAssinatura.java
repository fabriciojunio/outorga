package br.com.outorga.domain.billing;

public enum StatusDaAssinatura {
    /** Período de teste concedido pelo plano. */
    EM_TESTE,
    /** Em dia. */
    ATIVA,
    /** Cobrança falhou. Ainda assiste enquanto durar a carencia. */
    INADIMPLENTE,
    /** Cancelada pelo assinante, com acesso até o fim do ciclo pago. */
    CANCELADA,
    /** Sem acesso. Fim de linha. */
    ENCERRADA
}
