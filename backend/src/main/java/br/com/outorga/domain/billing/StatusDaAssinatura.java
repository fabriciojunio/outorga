package br.com.outorga.domain.billing;

public enum StatusDaAssinatura {
    /** Periodo de teste concedido pelo plano. */
    EM_TESTE,
    /** Em dia. */
    ATIVA,
    /** Cobranca falhou. Ainda assiste enquanto durar a carencia. */
    INADIMPLENTE,
    /** Cancelada pelo assinante, com acesso ate o fim do ciclo pago. */
    CANCELADA,
    /** Sem acesso. Fim de linha. */
    ENCERRADA
}
