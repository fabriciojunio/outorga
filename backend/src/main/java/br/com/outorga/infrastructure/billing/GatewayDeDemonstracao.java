package br.com.outorga.infrastructure.billing;

import br.com.outorga.application.ports.GatewayDePagamento;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.infrastructure.config.ConfiguracaoDaOutorga;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Cobranca simulada, usada enquanto nao ha chave de gateway.
 *
 * A URL de checkout que ela devolve aponta para um endereco do proprio
 * sistema, que dispara o mesmo webhook que o gateway real dispararia. O
 * caminho do dinheiro fica exercitado de ponta a ponta, incluindo a mudanca de
 * status da assinatura e a trilha de auditoria, sem nenhum centavo circulando.
 *
 * Um cuidado deliberado: esta classe so entra em cena com o sistema em
 * DEMONSTRACAO. Em PRODUCAO sem chave de gateway o sistema recusa subir, em
 * vez de silenciosamente liberar assinatura de graca para todo mundo, que e o
 * jeito mais caro de descobrir que faltou uma variavel de ambiente.
 */
public class GatewayDeDemonstracao implements GatewayDePagamento {

    private final ObjectMapper json;
    private final String urlPublica;

    public GatewayDeDemonstracao(ConfiguracaoDaOutorga configuracao, ObjectMapper json) {
        this.json = json;
        this.urlPublica = configuracao.urlPublicaDaApi();
    }

    @Override
    public Result<Cobranca> abrirAssinatura(PedidoDeAssinatura pedido) {
        var referencia = "demo_" + UUID.randomUUID().toString().substring(0, 12);
        var checkout = urlPublica + "/api/v1/publico/checkout-simulado?referencia=" + referencia
                + "&valor=" + pedido.valor().centavos();
        return Result.ok(new Cobranca(referencia, checkout,
                "00020126BR.GOV.BCB.PIX-SIMULADO-" + referencia));
    }

    @Override
    public Result<Void> cancelarAssinatura(String referenciaNoGateway) {
        return Result.ok(null);
    }

    /**
     * Em demonstracao o webhook so aceita chamada vinda do proprio simulador,
     * marcada por um cabecalho interno. Nao e seguranca de verdade, e nem
     * precisa ser: o modo inteiro nao move dinheiro.
     */
    @Override
    public boolean webhookAutentico(Map<String, String> cabecalhos, String corpo) {
        return "simulador".equals(cabecalhos.get("x-outorga-origem"));
    }

    @Override
    public Result<EventoDeCobranca> interpretar(String corpo) {
        try {
            var no = json.readTree(corpo);
            var tipo = switch (no.path("evento").asText("")) {
                case "CONFIRMADO" -> EventoDeCobranca.Tipo.CONFIRMADO;
                case "RECUSADO" -> EventoDeCobranca.Tipo.RECUSADO;
                case "ESTORNADO" -> EventoDeCobranca.Tipo.ESTORNADO;
                default -> EventoDeCobranca.Tipo.IGNORADO;
            };
            var valor = no.hasNonNull("centavos")
                    ? Dinheiro.reais(no.get("centavos").asLong()) : null;
            return Result.ok(new EventoDeCobranca(tipo, no.path("referencia").asText(null), valor,
                    "simulacao"));
        } catch (Exception e) {
            return Result.erro(new FalhaDeNegocio("WEBHOOK_ILEGIVEL",
                    "Corpo do webhook simulado nao pode ser lido"));
        }
    }
}
