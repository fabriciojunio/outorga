package br.com.mirante.application.usecases.billing;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.GatewayDePagamento;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.billing.Assinatura;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.util.Map;

/**
 * Cancelamento pelo proprio assinante. Nao corta o acesso na hora: o ciclo
 * pago vale ate o fim, porque cobrar por um mes e tirar no dia 3 e o tipo de
 * coisa que rende reclamacao no Reclame Aqui e chargeback.
 */
public class CancelarAssinatura {

    private final Repositorios.DeAssinatura assinaturas;
    private final GatewayDePagamento gateway;
    private final Auditor auditor;
    private final Clock relogio;

    public CancelarAssinatura(Repositorios.DeAssinatura assinaturas, GatewayDePagamento gateway,
                              Auditor auditor, Clock relogio) {
        this.assinaturas = assinaturas;
        this.gateway = gateway;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public Result<Assinatura> executar(ContextoDoChamador chamador, String motivo) {
        var achada = assinaturas.vigenteDoUsuario(chamador.tenantId(), chamador.usuarioId());
        if (achada.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Assinatura"));
        }
        var assinatura = achada.get();

        var cancelamento = assinatura.cancelar(motivo, relogio.instant());
        if (cancelamento.falhou()) {
            return cancelamento;
        }

        if (assinatura.referenciaNoGateway() != null) {
            gateway.cancelarAssinatura(assinatura.referenciaNoGateway());
        }
        assinaturas.salvar(assinatura);

        auditor.registrar(chamador, AcaoAuditavel.ASSINATURA_CANCELADA, "assinatura",
                assinatura.id().toString(),
                Map.of("motivo", motivo == null ? "" : motivo,
                        "acessoAte", String.valueOf(assinatura.fimDoCicloAtual())));
        return Result.ok(assinatura);
    }
}
