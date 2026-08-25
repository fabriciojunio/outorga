package br.com.mirante.application.usecases.billing;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;

import java.time.Clock;
import java.util.Map;

/**
 * Passa o pente nas assinaturas que venceram e nao voltaram. Sem isso, quem
 * cancelou em janeiro continua assistindo em marco, porque nada no sistema
 * repara sozinho na passagem do tempo.
 */
public class EncerrarAssinaturasVencidas {

    private final Repositorios.DeAssinatura assinaturas;
    private final Auditor auditor;
    private final Clock relogio;

    public EncerrarAssinaturasVencidas(Repositorios.DeAssinatura assinaturas, Auditor auditor,
                                       Clock relogio) {
        this.assinaturas = assinaturas;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public int executar() {
        var agora = relogio.instant();
        int encerradas = 0;
        for (var assinatura : assinaturas.vencendoAte(agora)) {
            if (assinatura.aplicarPassagemDoTempo(agora)) {
                assinaturas.salvar(assinatura);
                encerradas++;
                auditor.registrar(ContextoDoChamador.doSistema(assinatura.tenantId()),
                        AcaoAuditavel.ASSINATURA_CANCELADA, "assinatura",
                        assinatura.id().toString(),
                        Map.of("motivo", "vencimento sem pagamento"));
            }
        }
        return encerradas;
    }
}
