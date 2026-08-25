package br.com.mirante.domain.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * Linha do tempo da assinatura. Existe para responder "por que esse assinante
 * perdeu acesso no dia 12" sem depender de log de aplicacao.
 */
public record EventoDaAssinatura(UUID id, UUID assinaturaId, TipoDeEvento tipo, String detalhe,
                                 Instant ocorridoEm) {

    public enum TipoDeEvento {
        CRIADA,
        TESTE_INICIADO,
        PAGAMENTO_CONFIRMADO,
        PAGAMENTO_FALHOU,
        ENTROU_EM_CARENCIA,
        CANCELAMENTO_PEDIDO,
        ENCERRADA,
        PLANO_TROCADO,
        REATIVADA
    }

    public static EventoDaAssinatura de(UUID assinaturaId, TipoDeEvento tipo, String detalhe,
                                        Instant agora) {
        return new EventoDaAssinatura(UUID.randomUUID(), assinaturaId, tipo, detalhe, agora);
    }
}
