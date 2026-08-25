package br.com.mirante.application.ports;

import br.com.mirante.domain.billing.Dinheiro;
import br.com.mirante.shared.Result;

import java.util.Map;
import java.util.UUID;

/**
 * Porta de cobranca. A implementacao de partida e o Asaas, que cobre PIX,
 * cartao e assinatura recorrente com webhook.
 *
 * Nenhum dado de cartao passa por dentro do Mirante: o gateway devolve um
 * endereco de checkout e o sistema guarda so a referencia da cobranca.
 */
public interface GatewayDePagamento {

    Result<Cobranca> abrirAssinatura(PedidoDeAssinatura pedido);

    Result<Void> cancelarAssinatura(String referenciaNoGateway);

    /** Confere a assinatura do webhook antes de qualquer efeito colateral. */
    boolean webhookAutentico(Map<String, String> cabecalhos, String corpo);

    Result<EventoDeCobranca> interpretar(String corpo);

    record PedidoDeAssinatura(UUID assinaturaId, String nomeDoCliente, String emailDoCliente,
                              String documentoDoCliente, Dinheiro valor, int diasAteVencer,
                              String descricao, String urlDeRetorno) {
    }

    record Cobranca(String referenciaNoGateway, String urlDeCheckout, String pixCopiaECola) {
    }

    record EventoDeCobranca(Tipo tipo, String referenciaNoGateway, Dinheiro valor, String motivo) {

        public enum Tipo {
            CONFIRMADO,
            RECUSADO,
            ESTORNADO,
            IGNORADO
        }
    }
}
