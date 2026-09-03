package br.com.outorga.application;

import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.Periodicidade;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Email;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.identity.Usuario;
import br.com.outorga.domain.rights.JanelaDeLicenca;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.domain.tenant.Marca;
import br.com.outorga.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

/**
 * Cenario compartilhado pelos testes de caso de uso: um cliente ativo, um
 * plano, um assinante em dia, uma licença vigente e um filme no ar.
 *
 * O relógio e fixo. Teste que depende de {@code Instant.now()} passa hoje e
 * quebra na virada do mes por motivo nenhum.
 */
public class CenarioDeTeste {

    public static final Instant AGORA = Instant.parse("2026-08-24T20:00:00Z");

    public final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);

    public final Dubles.Tenants tenants = new Dubles.Tenants();
    public final Dubles.Usuarios usuarios = new Dubles.Usuarios();
    public final Dubles.Perfis perfis = new Dubles.Perfis();
    public final Dubles.Dispositivos dispositivos = new Dubles.Dispositivos();
    public final Dubles.Titulos titulos = new Dubles.Titulos();
    public final Dubles.Licencas licencas = new Dubles.Licencas();
    public final Dubles.Planos planos = new Dubles.Planos();
    public final Dubles.Assinaturas assinaturas = new Dubles.Assinaturas();
    public final Dubles.Cupons cupons = new Dubles.Cupons();
    public final Dubles.Canais canais = new Dubles.Canais();
    public final Dubles.Epg epg = new Dubles.Epg();
    public final Dubles.Sessoes sessoes = new Dubles.Sessoes();
    public final Dubles.Auditorias auditorias = new Dubles.Auditorias();
    public final Dubles.Cifrador cifrador = new Dubles.Cifrador();
    public final Dubles.Emissor emissor = new Dubles.Emissor();
    public final Dubles.Entrega entrega = new Dubles.Entrega();
    public final Dubles.Gateway gateway = new Dubles.Gateway();

    public final Auditor auditor = new Auditor(auditorias, relogio);

    public Tenant tenant;
    public Usuario assinante;
    public Usuario editor;
    public Perfil perfil;
    public Plano plano;
    public Assinatura assinatura;
    public Licenca licenca;
    public Titulo filme;

    public CenarioDeTeste() {
        tenant = Tenant.abrir("cineserra", "Cine Serra", "12345678000190",
                Marca.padrao("Cine Serra"), AGORA.minus(Duration.ofDays(90))).valorOuFalha();
        tenant.liberarParaProducao();
        tenants.salvar(tenant);

        assinante = Usuario.criar(tenant.id(), new Email("assinante@exemplo.com"),
                cifrador.cifrar("senha-do-assinante"), "Maria", Set.of(Papel.ASSINANTE), AGORA)
                .valorOuFalha();
        usuarios.salvar(assinante);

        editor = Usuario.criar(tenant.id(), new Email("editor@exemplo.com"),
                cifrador.cifrar("senha-do-editor"), "Joao", Set.of(Papel.EDITOR), AGORA)
                .valorOuFalha();
        usuarios.salvar(editor);

        perfil = Perfil.criar(assinante.id(), "Maria", ClassificacaoIndicativa.DEZESSEIS_ANOS,
                false, 0).valorOuFalha();
        perfis.salvar(perfil);

        plano = Plano.criar(tenant.id(), "Familia", Dinheiro.reais(2490), Periodicidade.MENSAL, 2,
                Qualidade.FULL_HD).valorOuFalha();
        planos.salvar(plano);

        assinatura = Assinatura.abrir(tenant.id(), assinante.id(), plano,
                AGORA.minus(Duration.ofDays(10))).valorOuFalha();
        assinatura.confirmarPagamento(plano, AGORA.minus(Duration.ofDays(10)));
        assinatura.vincularAoGateway("ref-teste");
        assinaturas.salvar(assinatura);

        licenca = Licenca.cadastrar(tenant.id(), "Produtora Serra", "CT-2026-001",
                Set.of(Territorio.BRASIL),
                new JanelaDeLicenca(AGORA.minus(Duration.ofDays(60)), AGORA.plus(Duration.ofDays(5))),
                Set.of(TipoDeDispositivo.WEB, TipoDeDispositivo.ANDROID)).valorOuFalha();
        licenca.anexarComprovacao("s3://contratos/ct-2026-001.pdf");
        licencas.salvar(licenca);

        filme = Titulo.criarFilme(tenant.id(), "Estrada de Terra", ClassificacaoIndicativa.DOZE_ANOS,
                Duration.ofMinutes(96)).valorOuFalha();
        filme.definirVideoDoFilme("acervo/estrada-de-terra");
        filme.publicar(licenca, AGORA.minus(Duration.ofDays(20)));
        titulos.salvar(filme);
    }

    public ContextoDoChamador comoAssinante() {
        return new ContextoDoChamador(tenant.id(), assinante.id(), "assinante",
                Set.of(Papel.ASSINANTE), "203.0.113.10");
    }

    public ContextoDoChamador comoEditor() {
        return new ContextoDoChamador(tenant.id(), editor.id(), "editor",
                Set.of(Papel.EDITOR), "203.0.113.11");
    }

    public Clock relogioEm(Instant momento) {
        return Clock.fixed(momento, ZoneOffset.UTC);
    }

    public UUID tenantId() {
        return tenant.id();
    }
}
