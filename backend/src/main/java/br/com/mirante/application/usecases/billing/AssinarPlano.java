package br.com.mirante.application.usecases.billing;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.GatewayDePagamento;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.billing.Assinatura;
import br.com.mirante.domain.billing.Cupom;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Abre a assinatura no Mirante e a cobranca no gateway.
 *
 * A assinatura nasce antes da cobranca de proposito. Se o gateway cair no
 * meio, sobra um registro INADIMPLENTE que o webhook conserta quando o
 * pagamento entrar, em vez de um pagamento no gateway sem contraparte aqui,
 * que e o tipo de furo que so aparece na conciliacao do fim do mes.
 */
public class AssinarPlano {

    private final Repositorios.DeAssinatura assinaturas;
    private final Repositorios.DePlano planos;
    private final Repositorios.DeUsuario usuarios;
    private final Repositorios.DeCupom cupons;
    private final GatewayDePagamento gateway;
    private final Auditor auditor;
    private final Clock relogio;

    public AssinarPlano(Repositorios.DeAssinatura assinaturas, Repositorios.DePlano planos,
                        Repositorios.DeUsuario usuarios, Repositorios.DeCupom cupons,
                        GatewayDePagamento gateway, Auditor auditor, Clock relogio) {
        this.assinaturas = assinaturas;
        this.planos = planos;
        this.usuarios = usuarios;
        this.cupons = cupons;
        this.gateway = gateway;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record Entrada(UUID planoId, String codigoDoCupom, String documento, String urlDeRetorno) {
    }

    public record Saida(UUID assinaturaId, String urlDeCheckout, String pixCopiaECola,
                        String valorFormatado) {
    }

    public Result<Saida> executar(ContextoDoChamador chamador, Entrada entrada) {
        var agora = relogio.instant();

        var usuario = usuarios.porId(chamador.tenantId(), chamador.usuarioId());
        if (usuario.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Usuario"));
        }
        var plano = planos.porId(chamador.tenantId(), entrada.planoId());
        if (plano.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Plano"));
        }

        var jaTem = assinaturas.vigenteDoUsuario(chamador.tenantId(), chamador.usuarioId());
        if (jaTem.isPresent() && jaTem.get().permiteAssistir(agora)) {
            return Result.erro(new FalhaDeNegocio("JA_ASSINA",
                    "Esta conta ja tem assinatura ativa. Troque de plano em vez de abrir outra"));
        }

        Cupom cupom = null;
        if (entrada.codigoDoCupom() != null && !entrada.codigoDoCupom().isBlank()) {
            var achado = cupons.porCodigo(chamador.tenantId(), entrada.codigoDoCupom());
            if (achado.isEmpty()) {
                return Result.erro(new FalhaDeNegocio("CUPOM_INEXISTENTE", "Cupom nao encontrado"));
            }
            var resgate = achado.get().resgatar(agora);
            if (resgate.falhou()) {
                return Result.erro(resgate.falha().orElseThrow());
            }
            cupom = cupons.salvar(achado.get());
        }

        var abertura = Assinatura.abrir(chamador.tenantId(), chamador.usuarioId(), plano.get(), agora);
        if (abertura.falhou()) {
            return Result.erro(abertura.falha().orElseThrow());
        }
        Assinatura assinatura = abertura.valorOuFalha();
        assinaturas.salvar(assinatura);

        var valor = plano.get().precoCom(cupom);
        var cobranca = gateway.abrirAssinatura(new GatewayDePagamento.PedidoDeAssinatura(
                assinatura.id(),
                usuario.get().nome(),
                usuario.get().email().valor(),
                entrada.documento(),
                valor,
                plano.get().diasDeTeste(),
                plano.get().nome(),
                entrada.urlDeRetorno()));

        if (cobranca.falhou()) {
            return Result.erro(cobranca.falha().orElseThrow());
        }

        assinatura.vincularAoGateway(cobranca.valorOuFalha().referenciaNoGateway());
        assinaturas.salvar(assinatura);

        auditor.registrar(chamador, AcaoAuditavel.ASSINATURA_ABERTA, "assinatura",
                assinatura.id().toString(),
                Map.of("plano", plano.get().nome(),
                        "valor", valor.formatado(),
                        "cupom", cupom == null ? "" : cupom.codigo()));

        return Result.ok(new Saida(assinatura.id(),
                cobranca.valorOuFalha().urlDeCheckout(),
                cobranca.valorOuFalha().pixCopiaECola(),
                valor.formatado()));
    }
}
