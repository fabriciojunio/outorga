package br.com.mirante.application.usecases.billing;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.GatewayDePagamento;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.util.Map;

/**
 * Webhook de cobranca.
 *
 * Duas coisas nao sao negociaveis aqui. A assinatura do webhook e conferida
 * antes de qualquer leitura do corpo, porque este endereco e publico. E o
 * processamento e idempotente por natureza: confirmar duas vezes o mesmo
 * pagamento estende o ciclo a partir do fim vigente, entao reentrega de
 * webhook nao vira mes de graca.
 */
public class ProcessarEventoDeCobranca {

    private final Repositorios.DeAssinatura assinaturas;
    private final Repositorios.DePlano planos;
    private final GatewayDePagamento gateway;
    private final Auditor auditor;
    private final Clock relogio;

    public ProcessarEventoDeCobranca(Repositorios.DeAssinatura assinaturas, Repositorios.DePlano planos,
                                     GatewayDePagamento gateway, Auditor auditor, Clock relogio) {
        this.assinaturas = assinaturas;
        this.planos = planos;
        this.gateway = gateway;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public Result<String> executar(Map<String, String> cabecalhos, String corpo) {
        if (!gateway.webhookAutentico(cabecalhos, corpo)) {
            return Result.erro(new FalhaDeNegocio("WEBHOOK_NAO_AUTENTICO",
                    "Assinatura do webhook nao confere"));
        }

        var leitura = gateway.interpretar(corpo);
        if (leitura.falhou()) {
            return Result.erro(leitura.falha().orElseThrow());
        }
        var evento = leitura.valorOuFalha();

        if (evento.tipo() == GatewayDePagamento.EventoDeCobranca.Tipo.IGNORADO) {
            return Result.ok("evento ignorado");
        }

        var achada = assinaturas.porReferenciaNoGateway(evento.referenciaNoGateway());
        if (achada.isEmpty()) {
            // Nao e erro do gateway: pode ser cobranca de outro ambiente
            // apontando para a mesma URL. Responder 200 evita reentrega infinita.
            return Result.ok("assinatura nao encontrada para " + evento.referenciaNoGateway());
        }

        var assinatura = achada.get();
        var chamador = ContextoDoChamador.doSistema(assinatura.tenantId());
        var agora = relogio.instant();

        var plano = planos.porId(assinatura.tenantId(), assinatura.planoId());
        if (plano.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Plano da assinatura"));
        }

        switch (evento.tipo()) {
            case CONFIRMADO -> {
                var confirmacao = assinatura.confirmarPagamento(plano.get(), agora);
                if (confirmacao.falhou()) {
                    return Result.erro(confirmacao.falha().orElseThrow());
                }
                assinaturas.salvar(assinatura);
                auditor.registrar(chamador, AcaoAuditavel.PAGAMENTO_CONFIRMADO, "assinatura",
                        assinatura.id().toString(),
                        Map.of("valor", evento.valor() == null ? "" : evento.valor().formatado(),
                                "cicloAte", String.valueOf(assinatura.fimDoCicloAtual())));
            }
            case RECUSADO, ESTORNADO -> {
                var falha = assinatura.registrarFalhaDePagamento(evento.motivo(), agora);
                if (falha.falhou()) {
                    return Result.erro(falha.falha().orElseThrow());
                }
                assinaturas.salvar(assinatura);
                auditor.registrar(chamador, AcaoAuditavel.PAGAMENTO_RECUSADO, "assinatura",
                        assinatura.id().toString(),
                        Map.of("motivo", String.valueOf(evento.motivo()),
                                "carenciaAte", String.valueOf(assinatura.fimDaCarencia())));
            }
            default -> {
                return Result.ok("evento ignorado");
            }
        }

        return Result.ok("processado: " + evento.tipo().name());
    }
}
