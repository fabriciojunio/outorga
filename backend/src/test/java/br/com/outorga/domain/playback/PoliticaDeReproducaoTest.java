package br.com.outorga.domain.playback;

import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.Periodicidade;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Dispositivo;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.rights.JanelaDeLicenca;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.domain.tenant.Marca;
import br.com.outorga.domain.tenant.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A decisao de dar play tem muitos jeitos de dar errado, e cada um precisa
 * chegar no espectador com o motivo certo. Este teste cobre um por um, porque
 * "conteudo indisponivel" para tudo e o que gera chamado de suporte que
 * ninguem consegue responder.
 */
@DisplayName("Politica de reproducao")
class PoliticaDeReproducaoTest {

    private static final Instant AGORA = Instant.parse("2026-08-24T20:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();

    private final PoliticaDeReproducao politica = new PoliticaDeReproducao();

    private Tenant tenant;
    private Plano plano;
    private Assinatura assinatura;
    private Titulo titulo;
    private Licenca licenca;
    private Perfil perfil;
    private Dispositivo dispositivo;

    @BeforeEach
    void montarCenarioFeliz() {
        tenant = Tenant.abrir("cineserra", "Cine Serra", null, Marca.padrao("Cine Serra"),
                AGORA.minus(Duration.ofDays(60))).valorOuFalha();
        tenant.liberarParaProducao();

        plano = Plano.criar(TENANT, "Familia", Dinheiro.reais(2490), Periodicidade.MENSAL, 2,
                Qualidade.FULL_HD).valorOuFalha();

        assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA.minus(Duration.ofDays(5)))
                .valorOuFalha();
        assinatura.confirmarPagamento(plano, AGORA.minus(Duration.ofDays(5)));

        licenca = Licenca.cadastrar(TENANT, "Produtora Serra", "CT-2026-001",
                Set.of(Territorio.BRASIL),
                new JanelaDeLicenca(AGORA.minus(Duration.ofDays(30)), AGORA.plus(Duration.ofDays(15))),
                Set.of(TipoDeDispositivo.WEB, TipoDeDispositivo.ANDROID)).valorOuFalha();
        licenca.anexarComprovacao("s3://contrato.pdf");

        titulo = Titulo.criarFilme(TENANT, "Estrada de Terra", ClassificacaoIndicativa.DOZE_ANOS,
                Duration.ofMinutes(96)).valorOuFalha();
        titulo.definirVideoDoFilme("acervo/estrada");
        titulo.publicar(licenca, AGORA.minus(Duration.ofDays(10)));

        perfil = Perfil.criar(USUARIO, "Fabricio", ClassificacaoIndicativa.DEZESSEIS_ANOS, false, 0)
                .valorOuFalha();
        dispositivo = Dispositivo.registrar(USUARIO, "aparelho-1", TipoDeDispositivo.WEB,
                "Notebook", AGORA).valorOuFalha();
    }

    private ContextoDeReproducao contexto() {
        return new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo, licenca,
                dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 0, "acervo/estrada", AGORA);
    }

    /**
     * O Tenant gera o proprio id, e o cenario precisa que titulo e licenca
     * pertencam a ele. Reconstituir com o id fixo mantem tudo coerente.
     */
    private Tenant tenantComId() {
        return Tenant.reconstituir(TENANT, tenant.slug(), tenant.nome(), null, null, tenant.marca(),
                tenant.status(), tenant.criadoEm(), tenant.fimDoTeste(),
                tenant.motivoDaSuspensao());
    }

    @Test
    @DisplayName("autoriza quando tudo esta em ordem")
    void autorizaCenarioFeliz() {
        var decisao = politica.decidir(contexto());

        assertThat(decisao.sucesso()).isTrue();
        var autorizacao = decisao.valorOuFalha();
        assertThat(autorizacao.referenciaDoVideo()).isEqualTo("acervo/estrada");
        assertThat(autorizacao.qualidade()).isEqualTo(Qualidade.FULL_HD);
        assertThat(autorizacao.licencaId()).isEqualTo(licenca.id());
        assertThat(autorizacao.expiraEm())
                .isEqualTo(AGORA.plus(PoliticaDeReproducao.VALIDADE_DO_TOKEN));
    }

    @Test
    @DisplayName("rebaixa a qualidade ao teto do plano")
    void rebaixaQualidade() {
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.ULTRA_HD, 0, "acervo/estrada",
                AGORA);

        assertThat(politica.decidir(contexto).valorOuFalha().qualidade())
                .isEqualTo(Qualidade.FULL_HD);
    }

    @Test
    @DisplayName("recusa quando o cliente esta suspenso")
    void recusaTenantSuspenso() {
        tenant.suspender("inadimplencia");

        assertThat(codigoDaRecusa()).isEqualTo("SERVICO_INDISPONIVEL");
    }

    @Test
    @DisplayName("recusa quando a assinatura nao da acesso")
    void recusaAssinaturaSemAcesso() {
        assinatura.cancelar("desistiu", AGORA);
        assinatura.aplicarPassagemDoTempo(AGORA.plus(Duration.ofDays(400)));

        assertThat(codigoDaRecusa()).isEqualTo("ASSINATURA_SEM_ACESSO");
    }

    @Test
    @DisplayName("recusa titulo fora do ar, dizendo que e questao de direitos")
    void recusaTituloBloqueado() {
        titulo.revisarDireitos(null, AGORA);

        var decisao = politica.decidir(contexto());
        assertThat(decisao.falha().orElseThrow().codigo()).isEqualTo("TITULO_FORA_DO_AR");
        assertThat(decisao.falha().orElseThrow().mensagem())
                .isEqualTo("Titulo indisponivel por questao de direitos");
    }

    /**
     * A data escolhida fica dentro do ciclo pago e fora da janela da licenca.
     * Sem esse cuidado o teste passaria pelo motivo errado: a assinatura
     * venceria antes e a recusa sairia como falta de pagamento.
     */
    @Test
    @DisplayName("recusa quando a licenca venceu enquanto o titulo seguia no ar")
    void recusaLicencaVencida() {
        var depoisDaLicenca = AGORA.plus(Duration.ofDays(20));
        assertThat(assinatura.permiteAssistir(depoisDaLicenca)).isTrue();
        assertThat(licenca.vigenteEm(depoisDaLicenca)).isFalse();

        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 0, "acervo/estrada",
                depoisDaLicenca);

        assertThat(politica.decidir(contexto).falha().orElseThrow().codigo())
                .isEqualTo("LICENCA_NAO_VIGENTE");
    }

    @Test
    @DisplayName("recusa fora do territorio licenciado")
    void recusaForaDoTerritorio() {
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo,
                licenca, dispositivo, new Territorio("PT"), Qualidade.FULL_HD, 0, "acervo/estrada",
                AGORA);

        var falha = politica.decidir(contexto).falha().orElseThrow();
        assertThat(falha.codigo()).isEqualTo("FORA_DO_TERRITORIO");
        assertThat(falha.detalhes()).containsEntry("territorio", "PT");
    }

    @Test
    @DisplayName("recusa aparelho que o contrato nao autoriza")
    void recusaDispositivoNaoLicenciado() {
        var tv = Dispositivo.registrar(USUARIO, "tv-1", TipoDeDispositivo.TV_CONECTADA, "Sala",
                AGORA).valorOuFalha();
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo,
                licenca, tv, Territorio.BRASIL, Qualidade.FULL_HD, 0, "acervo/estrada", AGORA);

        assertThat(politica.decidir(contexto).falha().orElseThrow().codigo())
                .isEqualTo("DISPOSITIVO_NAO_LICENCIADO");
    }

    @Test
    @DisplayName("recusa conteudo acima da classificacao do perfil")
    void recusaControleParental() {
        var infantil = Perfil.criar(USUARIO, "Kids", null, true, 1).valorOuFalha();
        var contexto = new ContextoDeReproducao(tenantComId(), infantil, assinatura, plano, titulo,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 0, "acervo/estrada",
                AGORA);

        var falha = politica.decidir(contexto).falha().orElseThrow();
        assertThat(falha.codigo()).isEqualTo("BLOQUEADO_PELO_CONTROLE_PARENTAL");
        assertThat(falha.detalhes()).containsEntry("classificacao", "12");
    }

    @Test
    @DisplayName("recusa quando o limite de telas do plano ja esta ocupado")
    void recusaLimiteDeTelas() {
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 2, "acervo/estrada",
                AGORA);

        var falha = politica.decidir(contexto).falha().orElseThrow();
        assertThat(falha.codigo()).isEqualTo("LIMITE_DE_TELAS");
        assertThat(falha.detalhes()).containsEntry("telasDoPlano", 2);
    }

    @Test
    @DisplayName("aceita a ultima tela livre do plano")
    void aceitaUltimaTela() {
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 1, "acervo/estrada",
                AGORA);

        assertThat(politica.decidir(contexto).sucesso()).isTrue();
    }

    @Test
    @DisplayName("recusa quando o arquivo de video ainda nao existe")
    void recusaSemVideo() {
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, titulo,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 0, null, AGORA);

        assertThat(politica.decidir(contexto).falha().orElseThrow().codigo())
                .isEqualTo("VIDEO_INDISPONIVEL");
    }

    @Test
    @DisplayName("recusa titulo de outro cliente mesmo com tudo mais em ordem")
    void recusaTituloDeOutroTenant() {
        var deOutro = Titulo.criarFilme(UUID.randomUUID(), "Alheio", ClassificacaoIndicativa.LIVRE,
                Duration.ofMinutes(80)).valorOuFalha();
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, deOutro,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 0, "x", AGORA);

        assertThat(politica.decidir(contexto).falha().orElseThrow().codigo())
                .isEqualTo("TITULO_DE_OUTRO_TENANT");
    }

    @Test
    @DisplayName("recusa titulo inexistente")
    void recusaTituloNulo() {
        var contexto = new ContextoDeReproducao(tenantComId(), perfil, assinatura, plano, null,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 0, null, AGORA);

        assertThat(politica.decidir(contexto).falha().orElseThrow().codigo())
                .isEqualTo("TITULO_NAO_ENCONTRADO");
    }

    @Test
    @DisplayName("perfil ausente nao bloqueia: a conta assiste sem escolher perfil")
    void perfilAusenteNaoBloqueia() {
        var contexto = new ContextoDeReproducao(tenantComId(), null, assinatura, plano, titulo,
                licenca, dispositivo, Territorio.BRASIL, Qualidade.FULL_HD, 0, "acervo/estrada",
                AGORA);

        var decisao = politica.decidir(contexto);
        assertThat(decisao.sucesso()).isTrue();
        assertThat(decisao.valorOuFalha().perfilId()).isNull();
    }

    private String codigoDaRecusa() {
        return politica.decidir(contexto()).falha().orElseThrow().codigo();
    }
}
