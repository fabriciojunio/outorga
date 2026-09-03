package br.com.outorga.infrastructure.billing;

import br.com.outorga.application.ports.GatewayDePagamento;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.infrastructure.config.ConfiguracaoDaOutorga;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cobrança pelo Asaas.
 *
 * Escolhido por um motivo comercial antes de tecnico: não tem mensalidade nem
 * taxa de adesao. Enquanto o produto não vende, o gateway não cobra, e isso e
 * o que permite deixar a plataforma no ar antes do primeiro cliente.
 *
 * Nenhum dado de cartão passa por este processo. O que sai daqui e um pedido
 * de cobrança; o que volta e uma URL de fatura e uma referência.
 */
public class GatewayAsaas implements GatewayDePagamento {

    private static final Logger log = LoggerFactory.getLogger(GatewayAsaas.class);
    private static final ZoneId FUSO_DE_COBRANCA = ZoneId.of("America/Sao_Paulo");

    private final RestClient http;
    private final ObjectMapper json;
    private final ConfiguracaoDaOutorga.Cobranca config;
    private final Clock relogio;

    public GatewayAsaas(ConfiguracaoDaOutorga.Cobranca config, ObjectMapper json, Clock relogio) {
        this.config = config;
        this.json = json;
        this.relogio = relogio;
        this.http = RestClient.builder()
                .baseUrl(config.urlBase())
                .defaultHeader("access_token", config.chaveDeApi())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Result<Cobranca> abrirAssinatura(PedidoDeAssinatura pedido) {
        try {
            var clienteId = garantirCliente(pedido);
            if (clienteId.falhou()) {
                return Result.erro(clienteId.falha().orElseThrow());
            }

            var vencimento = LocalDate.ofInstant(relogio.instant(), FUSO_DE_COBRANCA)
                    .plusDays(Math.max(pedido.diasAteVencer(), 0));

            var corpo = new LinkedHashMap<String, Object>();
            corpo.put("customer", clienteId.valorOuFalha());
            // UNDEFINED deixa o pagador escolher entre PIX, boleto e cartão na
            // própria fatura. Fixar a forma aqui só reduz conversao.
            corpo.put("billingType", "UNDEFINED");
            corpo.put("value", valorEmReais(pedido.valor()));
            corpo.put("nextDueDate", vencimento.toString());
            corpo.put("cycle", "MONTHLY");
            corpo.put("description", pedido.descricao());
            corpo.put("externalReference", pedido.assinaturaId().toString());

            var resposta = http.post().uri("/subscriptions").body(corpo)
                    .retrieve().body(JsonNode.class);
            if (resposta == null || !resposta.hasNonNull("id")) {
                return Result.erro(new FalhaDeNegocio("GATEWAY_SEM_RESPOSTA",
                        "O gateway não devolveu a assinatura criada"));
            }

            var referencia = resposta.get("id").asText();
            var fatura = primeiraFatura(referencia);
            return Result.ok(new Cobranca(referencia, fatura.get("url"), fatura.get("pix")));
        } catch (Exception e) {
            log.error("Falha ao abrir assinatura no gateway", e);
            return Result.erro(new FalhaDeNegocio("GATEWAY_INDISPONIVEL",
                    "Não foi possível abrir a cobrança agora. Tente de novo em instantes"));
        }
    }

    private Result<String> garantirCliente(PedidoDeAssinatura pedido) {
        var busca = http.get()
                .uri(uri -> uri.path("/customers").queryParam("email", pedido.emailDoCliente()).build())
                .retrieve().body(JsonNode.class);
        if (busca != null && busca.path("data").isArray() && !busca.path("data").isEmpty()) {
            return Result.ok(busca.path("data").get(0).get("id").asText());
        }

        var corpo = new LinkedHashMap<String, Object>();
        corpo.put("name", pedido.nomeDoCliente());
        corpo.put("email", pedido.emailDoCliente());
        if (pedido.documentoDoCliente() != null && !pedido.documentoDoCliente().isBlank()) {
            corpo.put("cpfCnpj", pedido.documentoDoCliente().replaceAll("\\D", ""));
        }
        var criado = http.post().uri("/customers").body(corpo).retrieve().body(JsonNode.class);
        if (criado == null || !criado.hasNonNull("id")) {
            return Result.erro(new FalhaDeNegocio("GATEWAY_CLIENTE",
                    "Não foi possível cadastrar o pagador no gateway"));
        }
        return Result.ok(criado.get("id").asText());
    }

    /** A URL que o assinante abre para pagar a primeira cobrança do ciclo. */
    private Map<String, String> primeiraFatura(String assinaturaNoGateway) {
        try {
            var pagamentos = http.get().uri("/subscriptions/{id}/payments", assinaturaNoGateway)
                    .retrieve().body(JsonNode.class);
            if (pagamentos != null && pagamentos.path("data").isArray()
                    && !pagamentos.path("data").isEmpty()) {
                var primeiro = pagamentos.path("data").get(0);
                var saida = new LinkedHashMap<String, String>();
                saida.put("url", primeiro.path("invoiceUrl").asText(null));
                saida.put("pix", null);
                return saida;
            }
        } catch (Exception e) {
            log.warn("Assinatura criada mas a fatura ainda não existe: {}", e.getMessage());
        }
        return Map.of();
    }

    @Override
    public Result<Void> cancelarAssinatura(String referenciaNoGateway) {
        try {
            http.delete().uri("/subscriptions/{id}", referenciaNoGateway).retrieve().toBodilessEntity();
            return Result.ok(null);
        } catch (Exception e) {
            log.error("Falha ao cancelar assinatura {} no gateway", referenciaNoGateway, e);
            return Result.erro(new FalhaDeNegocio("GATEWAY_INDISPONIVEL",
                    "O cancelamento foi registrado aqui, mas o gateway não respondeu"));
        }
    }

    /**
     * O Asaas manda um token fixo no cabeçalho de cada webhook. Comparacao em
     * tempo constante: comparar segredo com equals vaza o prefixo correto para
     * quem souber medir.
     */
    @Override
    public boolean webhookAutentico(Map<String, String> cabecalhos, String corpo) {
        if (config.segredoDoWebhook() == null || config.segredoDoWebhook().isBlank()) {
            log.error("Webhook recebido sem segredo configurado. Recusado.");
            return false;
        }
        var recebido = cabecalhos.getOrDefault("asaas-access-token",
                cabecalhos.get("Asaas-Access-Token"));
        if (recebido == null) {
            return false;
        }
        return MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8),
                config.segredoDoWebhook().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Result<EventoDeCobranca> interpretar(String corpo) {
        try {
            var no = json.readTree(corpo);
            var evento = no.path("event").asText("");
            var pagamento = no.path("payment");
            var referencia = pagamento.path("subscription").asText(null);
            if (referencia == null || referencia.isBlank()) {
                return Result.ok(new EventoDeCobranca(EventoDeCobranca.Tipo.IGNORADO, null, null,
                        "evento sem assinatura vinculada"));
            }

            var valor = pagamento.hasNonNull("value")
                    ? Dinheiro.deReais(BigDecimal.valueOf(pagamento.get("value").asDouble())
                            .toPlainString())
                    : null;

            var tipo = switch (evento) {
                case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> EventoDeCobranca.Tipo.CONFIRMADO;
                case "PAYMENT_OVERDUE", "PAYMENT_DELETED" -> EventoDeCobranca.Tipo.RECUSADO;
                case "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED" ->
                        EventoDeCobranca.Tipo.ESTORNADO;
                default -> EventoDeCobranca.Tipo.IGNORADO;
            };

            return Result.ok(new EventoDeCobranca(tipo, referencia, valor, evento));
        } catch (Exception e) {
            return Result.erro(new FalhaDeNegocio("WEBHOOK_ILEGIVEL",
                    "Corpo do webhook não pode ser lido"));
        }
    }

    private static String valorEmReais(Dinheiro valor) {
        return valor.emUnidades().toPlainString();
    }
}
