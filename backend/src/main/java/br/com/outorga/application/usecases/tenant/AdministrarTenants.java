package br.com.outorga.application.usecases.tenant;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.CifradorDeSenha;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.identity.Email;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.domain.identity.Usuario;
import br.com.outorga.domain.tenant.Marca;
import br.com.outorga.domain.tenant.Tenant;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Operacao da plataforma: abrir, suspender e encerrar cliente. So o
 * ADMIN_PLATAFORMA entra aqui.
 */
public class AdministrarTenants {

    public static final int DIAS_DE_TESTE_PADRAO = 14;

    private final Repositorios.DeTenant tenants;
    private final Repositorios.DeUsuario usuarios;
    private final CifradorDeSenha cifrador;
    private final Auditor auditor;
    private final Clock relogio;

    public AdministrarTenants(Repositorios.DeTenant tenants, Repositorios.DeUsuario usuarios,
                              CifradorDeSenha cifrador, Auditor auditor, Clock relogio) {
        this.tenants = tenants;
        this.usuarios = usuarios;
        this.cifrador = cifrador;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record PedidoDeAbertura(String slug, String nome, String documento, String dominioProprio,
                                   String nomeExibido, String logoUri, String corPrimaria,
                                   String corDeFundo, String emailDoDono, String nomeDoDono,
                                   String senhaDoDono) {
    }

    public record Aberto(Tenant tenant, UUID donoId) {
    }

    public Result<Aberto> abrir(ContextoDoChamador chamador, PedidoDeAbertura pedido) {
        if (!chamador.tem(Papel.ADMIN_PLATAFORMA)) {
            return Result.erro(Falhas.semPermissao("abrir cliente"));
        }
        if (tenants.porSlug(pedido.slug()).isPresent()) {
            return Result.erro(Falhas.conflito("Ja existe cliente com o identificador " + pedido.slug()));
        }

        Marca marca;
        try {
            marca = new Marca(
                    pedido.nomeExibido() == null ? pedido.nome() : pedido.nomeExibido(),
                    pedido.logoUri(), pedido.corPrimaria(), pedido.corDeFundo());
        } catch (IllegalArgumentException e) {
            return Result.erro(Falhas.invalido(e.getMessage()));
        }

        var agora = relogio.instant();
        var abertura = Tenant.abrir(pedido.slug(), pedido.nome(), pedido.documento(), marca, agora);
        if (abertura.falhou()) {
            return Result.erro(abertura.falha().orElseThrow());
        }
        var tenant = abertura.valorOuFalha();
        tenant.definirDominioProprio(pedido.dominioProprio());
        tenant.definirPeriodoDeTeste(agora.plus(Duration.ofDays(DIAS_DE_TESTE_PADRAO)));
        tenants.salvar(tenant);

        var email = Email.de(pedido.emailDoDono());
        if (email.falhou()) {
            return Result.erro(email.falha().orElseThrow());
        }
        var dono = Usuario.criar(tenant.id(), email.valorOuFalha(),
                cifrador.cifrar(pedido.senhaDoDono()), pedido.nomeDoDono(),
                Set.of(Papel.DONO), agora);
        if (dono.falhou()) {
            return Result.erro(dono.falha().orElseThrow());
        }
        usuarios.salvar(dono.valorOuFalha());

        auditor.registrar(chamador, AcaoAuditavel.TENANT_ABERTO, "tenant", tenant.id().toString(),
                Map.of("slug", tenant.slug(), "nome", tenant.nome()));

        return Result.ok(new Aberto(tenant, dono.valorOuFalha().id()));
    }

    public Result<Tenant> liberarParaProducao(ContextoDoChamador chamador, UUID tenantId) {
        if (!chamador.tem(Papel.ADMIN_PLATAFORMA)) {
            return Result.erro(Falhas.semPermissao("liberar cliente"));
        }
        var achado = tenants.porId(tenantId);
        if (achado.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Cliente"));
        }
        var liberado = achado.get().liberarParaProducao();
        if (liberado.falhou()) {
            return liberado;
        }
        tenants.salvar(achado.get());
        return Result.ok(achado.get());
    }

    public Result<Tenant> suspender(ContextoDoChamador chamador, UUID tenantId, String motivo) {
        if (!chamador.tem(Papel.ADMIN_PLATAFORMA)) {
            return Result.erro(Falhas.semPermissao("suspender cliente"));
        }
        var achado = tenants.porId(tenantId);
        if (achado.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Cliente"));
        }
        var suspenso = achado.get().suspender(motivo);
        if (suspenso.falhou()) {
            return suspenso;
        }
        tenants.salvar(achado.get());
        auditor.registrar(chamador, AcaoAuditavel.TENANT_SUSPENSO, "tenant", tenantId.toString(),
                Map.of("motivo", motivo == null ? "" : motivo));
        return Result.ok(achado.get());
    }
}
