package br.com.mirante.infrastructure.persistence;

import br.com.mirante.domain.billing.Assinatura;
import br.com.mirante.domain.billing.Cupom;
import br.com.mirante.domain.billing.Dinheiro;
import br.com.mirante.domain.billing.Periodicidade;
import br.com.mirante.domain.billing.Plano;
import br.com.mirante.domain.billing.Qualidade;
import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.domain.catalog.Episodio;
import br.com.mirante.domain.catalog.StatusDePublicacao;
import br.com.mirante.domain.catalog.Temporada;
import br.com.mirante.domain.catalog.Titulo;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.audit.RegistroDeAuditoria;
import br.com.mirante.domain.identity.Dispositivo;
import br.com.mirante.domain.identity.Email;
import br.com.mirante.domain.identity.Papel;
import br.com.mirante.domain.identity.Perfil;
import br.com.mirante.domain.identity.Usuario;
import br.com.mirante.domain.live.CanalAoVivo;
import br.com.mirante.domain.live.ProgramaEpg;
import br.com.mirante.domain.playback.Autorizacao;
import br.com.mirante.domain.playback.SessaoDeReproducao;
import br.com.mirante.domain.rights.JanelaDeLicenca;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.domain.rights.Territorio;
import br.com.mirante.domain.rights.TipoDeDispositivo;
import br.com.mirante.domain.tenant.Marca;
import br.com.mirante.domain.tenant.Tenant;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistencia contra um Postgres de verdade, subido pelo proprio teste.
 *
 * Esta classe existe porque o esquema usa coisa que banco em memoria nao
 * imita: arranjo de texto, jsonb, indice parcial e funcao SQL para busca sem
 * acento. Testar isso com H2 daria uma sensacao falsa de seguranca e o erro
 * apareceria no primeiro deploy.
 *
 * Roda no ciclo normal de testes. A primeira execucao baixa o binario do
 * Postgres e demora; as seguintes reaproveitam o cache do Maven.
 */
@DisplayName("Persistencia em Postgres")
class PersistenciaEmPostgresTest {

    private static final Instant AGORA = Instant.parse("2026-08-24T12:00:00Z");

    private static EmbeddedPostgres postgres;
    private static DataSource fonte;
    private static JdbcClient jdbc;

    private static PersistenciaDeIdentidade.DeTenant tenants;
    private static PersistenciaDeIdentidade.DeUsuario usuarios;
    private static PersistenciaDeIdentidade.DePerfil perfis;
    private static PersistenciaDeIdentidade.DeDispositivo dispositivos;
    private static PersistenciaDeCatalogo.DeLicenca licencas;
    private static PersistenciaDeCatalogo.DeTitulo titulos;
    private static PersistenciaComercial.DePlano planos;
    private static PersistenciaComercial.DeCupom cupons;
    private static PersistenciaComercial.DeAssinatura assinaturas;
    private static PersistenciaDeExibicao.DeCanal canais;
    private static PersistenciaDeExibicao.DeEpg epg;
    private static PersistenciaDeExibicao.DeSessao sessoes;
    private static PersistenciaDeExibicao.DeAuditoria auditoria;

    private Tenant tenant;

    @BeforeAll
    static void subirBanco() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        fonte = postgres.getPostgresDatabase();
        Flyway.configure().dataSource(fonte).locations("classpath:db/migration").load().migrate();

        jdbc = JdbcClient.create(fonte);
        tenants = new PersistenciaDeIdentidade.DeTenant(jdbc);
        usuarios = new PersistenciaDeIdentidade.DeUsuario(jdbc);
        perfis = new PersistenciaDeIdentidade.DePerfil(jdbc);
        dispositivos = new PersistenciaDeIdentidade.DeDispositivo(jdbc);
        licencas = new PersistenciaDeCatalogo.DeLicenca(jdbc);
        titulos = new PersistenciaDeCatalogo.DeTitulo(jdbc);
        planos = new PersistenciaComercial.DePlano(jdbc);
        cupons = new PersistenciaComercial.DeCupom(jdbc);
        assinaturas = new PersistenciaComercial.DeAssinatura(jdbc);
        canais = new PersistenciaDeExibicao.DeCanal(jdbc);
        epg = new PersistenciaDeExibicao.DeEpg(jdbc);
        sessoes = new PersistenciaDeExibicao.DeSessao(jdbc);
        auditoria = new PersistenciaDeExibicao.DeAuditoria(jdbc);
    }

    @AfterAll
    static void derrubarBanco() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void limparEcriarTenant() {
        new JdbcTemplate(fonte).execute("""
                truncate table auditoria, sessoes_reproducao, epg_programas, canais,
                               assinatura_eventos, assinaturas, cupons, planos, episodios,
                               temporadas, titulos, licencas, dispositivos, perfis,
                               refresh_tokens, usuarios, tenants
                restart identity cascade
                """);
        tenant = Tenant.abrir("cineserra", "Cine Serra", "12345678000190",
                new Marca("Cine Serra", "logo.svg", "#e6b800", "#0d0f14"), AGORA).valorOuFalha();
        tenant.liberarParaProducao();
        tenants.salvar(tenant);
    }

    @Nested
    @DisplayName("Cliente e conta")
    class DoClienteEconta {

        @Test
        @DisplayName("grava e le o cliente com marca e dominio")
        void tenantVaiEvolta() {
            tenant.definirDominioProprio("assista.cineserra.com.br");
            tenant.definirPeriodoDeTeste(AGORA.plus(Duration.ofDays(14)));
            tenants.salvar(tenant);

            var lido = tenants.porSlug("cineserra").orElseThrow();

            assertThat(lido.id()).isEqualTo(tenant.id());
            assertThat(lido.nome()).isEqualTo("Cine Serra");
            assertThat(lido.dominioProprio()).isEqualTo("assista.cineserra.com.br");
            assertThat(lido.marca().corPrimaria()).isEqualTo("#e6b800");
            assertThat(lido.fimDoTeste()).isEqualTo(AGORA.plus(Duration.ofDays(14)));
            assertThat(tenants.porDominio("assista.cineserra.com.br")).isPresent();
        }

        @Test
        @DisplayName("salvar de novo atualiza em vez de duplicar")
        void salvarDuasVezesAtualiza() {
            tenant.suspender("inadimplencia");
            tenants.salvar(tenant);

            assertThat(tenants.todos()).hasSize(1);
            assertThat(tenants.porId(tenant.id()).orElseThrow().motivoDaSuspensao())
                    .isEqualTo("inadimplencia");
        }

        @Test
        @DisplayName("usuario preserva papeis, bloqueio e anonimizacao")
        void usuarioVaiEvolta() {
            var usuario = Usuario.criar(tenant.id(), new Email("dono@exemplo.com"), "hash", "Dono",
                    Set.of(Papel.DONO, Papel.EDITOR), AGORA).valorOuFalha();
            usuario.registrarTentativaDeLogin(false, AGORA);
            usuarios.salvar(usuario);

            var lido = usuarios.porEmail(tenant.id(), new Email("dono@exemplo.com")).orElseThrow();

            assertThat(lido.papeis()).containsExactlyInAnyOrder(Papel.DONO, Papel.EDITOR);
            assertThat(lido.tentativasSeguidas()).isEqualTo(1);
            assertThat(usuarios.existeEmail(tenant.id(), new Email("dono@exemplo.com"))).isTrue();
            assertThat(usuarios.doTenant(tenant.id())).hasSize(1);

            lido.anonimizar(AGORA);
            usuarios.salvar(lido);

            var depois = usuarios.porId(tenant.id(), usuario.id()).orElseThrow();
            assertThat(depois.anonimizado()).isTrue();
            assertThat(depois.nome()).isEqualTo("Titular removido");
        }

        @Test
        @DisplayName("consulta por outro tenant nao acha nada")
        void isolamentoPorTenant() {
            var usuario = Usuario.criar(tenant.id(), new Email("dono@exemplo.com"), "hash", "Dono",
                    Set.of(Papel.DONO), AGORA).valorOuFalha();
            usuarios.salvar(usuario);

            var outro = Tenant.abrir("outra", "Outra TV", null, null, AGORA).valorOuFalha();
            tenants.salvar(outro);

            assertThat(usuarios.porId(outro.id(), usuario.id())).isEmpty();
            assertThat(usuarios.porEmail(outro.id(), new Email("dono@exemplo.com"))).isEmpty();
            assertThat(usuarios.doTenant(outro.id())).isEmpty();
        }

        @Test
        @DisplayName("perfil e dispositivo seguem a conta")
        void perfilEdispositivo() {
            var usuario = Usuario.criar(tenant.id(), new Email("a@exemplo.com"), "hash", "Maria",
                    Set.of(Papel.ASSINANTE), AGORA).valorOuFalha();
            usuarios.salvar(usuario);

            var perfil = Perfil.criar(usuario.id(), "Maria",
                    ClassificacaoIndicativa.DEZESSEIS_ANOS, false, 0).valorOuFalha();
            perfil.definirPin("hash-pin");
            perfis.salvar(perfil);

            var dispositivo = Dispositivo.registrar(usuario.id(), "aparelho-1",
                    TipoDeDispositivo.ANDROID, "Celular", AGORA).valorOuFalha();
            dispositivos.salvar(dispositivo);

            assertThat(perfis.quantidadeDoUsuario(usuario.id())).isEqualTo(1);
            assertThat(perfis.porId(perfil.id()).orElseThrow().protegidoPorPin()).isTrue();
            assertThat(dispositivos.porIdentificador(usuario.id(), "aparelho-1")).isPresent();
            assertThat(dispositivos.doUsuario(usuario.id())).hasSize(1);

            perfis.remover(perfil.id());
            dispositivos.remover(dispositivo.id());

            assertThat(perfis.doUsuario(usuario.id())).isEmpty();
            assertThat(dispositivos.doUsuario(usuario.id())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Licenca e catalogo")
    class DoCatalogo {

        private Licenca licencaVigente() {
            var licenca = Licenca.cadastrar(tenant.id(), "Produtora Serra", "CT-2026-001",
                    Set.of(Territorio.BRASIL, new Territorio("PT")),
                    new JanelaDeLicenca(AGORA.minus(Duration.ofDays(10)),
                            AGORA.plus(Duration.ofDays(30))),
                    Set.of(TipoDeDispositivo.WEB, TipoDeDispositivo.ANDROID)).valorOuFalha();
            licenca.anexarComprovacao("arquivo://ct-1.pdf");
            return licencas.salvar(licenca);
        }

        @Test
        @DisplayName("licenca preserva arranjos de territorio e dispositivo")
        void licencaVaiEvolta() {
            var salva = licencaVigente();

            var lida = licencas.porId(tenant.id(), salva.id()).orElseThrow();

            assertThat(lida.territorios()).containsExactlyInAnyOrder(Territorio.BRASIL,
                    new Territorio("PT"));
            assertThat(lida.dispositivosAutorizados()).containsExactlyInAnyOrder(
                    TipoDeDispositivo.WEB, TipoDeDispositivo.ANDROID);
            assertThat(lida.comprovacaoUri()).isEqualTo("arquivo://ct-1.pdf");
            assertThat(lida.vigenteEm(AGORA)).isTrue();
        }

        @Test
        @DisplayName("acha as licencas que vencem ate uma data")
        void achaVencendo() {
            licencaVigente();

            assertThat(licencas.vencendoAte(AGORA.plus(Duration.ofDays(60)))).hasSize(1);
            assertThat(licencas.vencendoAte(AGORA.plus(Duration.ofDays(5)))).isEmpty();
        }

        @Test
        @DisplayName("licenca por prazo indeterminado grava com fim nulo")
        void licencaIndeterminada() {
            var licenca = Licenca.cadastrar(tenant.id(), "Acervo proprio", "INTERNO-1",
                    Set.of(Territorio.MUNDIAL), JanelaDeLicenca.aPartirDe(AGORA),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            licencas.salvar(licenca);

            var lida = licencas.porId(tenant.id(), licenca.id()).orElseThrow();

            assertThat(lida.janela().indeterminada()).isTrue();
            assertThat(licencas.vencendoAte(AGORA.plus(Duration.ofDays(3650)))).isEmpty();
        }

        @Test
        @DisplayName("filme publicado volta com generos e vinculo de licenca")
        void filmeVaiEvolta() {
            var licenca = licencaVigente();
            var filme = Titulo.criarFilme(tenant.id(), "Estrada de Terra",
                    ClassificacaoIndicativa.DOZE_ANOS, Duration.ofMinutes(96)).valorOuFalha();
            filme.definirSinopse("um caminhoneiro atravessa o interior");
            filme.definirAnoDeProducao(2024);
            filme.definirCapa("capa.jpg");
            filme.adicionarGenero("Drama");
            filme.adicionarGenero("estrada");
            filme.definirVideoDoFilme("acervo/estrada");
            filme.publicar(licenca, AGORA);
            titulos.salvar(filme);

            var lido = titulos.porId(tenant.id(), filme.id()).orElseThrow();

            assertThat(lido.nome()).isEqualTo("Estrada de Terra");
            assertThat(lido.generos()).containsExactlyInAnyOrder("drama", "estrada");
            assertThat(lido.duracao()).isEqualTo(Duration.ofMinutes(96));
            assertThat(lido.licencaId()).isEqualTo(licenca.id());
            assertThat(lido.status()).isEqualTo(StatusDePublicacao.PUBLICADO);
            assertThat(lido.publicadoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("serie volta com temporadas e episodios na ordem")
        void serieVaiEvolta() {
            var licenca = licencaVigente();
            var serie = Titulo.criarSerie(tenant.id(), "Cerrado",
                    ClassificacaoIndicativa.QUATORZE_ANOS).valorOuFalha();
            var temporada = Temporada.criar(1, "Primeira temporada").valorOuFalha();
            temporada.adicionar(Episodio.criar(2, "A cheia", Duration.ofMinutes(44),
                    "acervo/s01e02").valorOuFalha());
            temporada.adicionar(Episodio.criar(1, "A cerca", Duration.ofMinutes(46),
                    "acervo/s01e01").valorOuFalha());
            serie.adicionarTemporada(temporada);
            serie.publicar(licenca, AGORA);
            titulos.salvar(serie);

            var lida = titulos.porId(tenant.id(), serie.id()).orElseThrow();

            assertThat(lida.temporadas()).hasSize(1);
            assertThat(lida.temporadas().get(0).episodios())
                    .extracting(Episodio::numero).containsExactly(1, 2);
            assertThat(lida.localizarEpisodio(1, 1).orElseThrow().referenciaDoVideo())
                    .isEqualTo("acervo/s01e01");
        }

        @Test
        @DisplayName("busca acha o titulo mesmo digitado sem acento")
        void buscaSemAcento() {
            var licenca = licencaVigente();
            var filme = Titulo.criarFilme(tenant.id(), "Coracao Sertanejo",
                    ClassificacaoIndicativa.LIVRE, Duration.ofMinutes(90)).valorOuFalha();
            filme.definirVideoDoFilme("acervo/coracao");
            filme.publicar(licenca, AGORA);
            titulos.salvar(filme);

            assertThat(titulos.buscar(tenant.id(), "coracao", 10)).hasSize(1);
            assertThat(titulos.buscar(tenant.id(), "CORACAO", 10)).hasSize(1);
            assertThat(titulos.buscar(tenant.id(), "sertanejo", 10)).hasSize(1);
            assertThat(titulos.buscar(tenant.id(), "faroeste", 10)).isEmpty();
        }

        @Test
        @DisplayName("paginacao do catalogo respeita limite e salto")
        void paginacao() {
            var licenca = licencaVigente();
            for (int i = 1; i <= 5; i++) {
                var filme = Titulo.criarFilme(tenant.id(), "Filme " + i,
                        ClassificacaoIndicativa.LIVRE, Duration.ofMinutes(90)).valorOuFalha();
                filme.definirVideoDoFilme("acervo/" + i);
                filme.publicar(licenca, AGORA.plusSeconds(i));
                titulos.salvar(filme);
            }

            assertThat(titulos.publicados(tenant.id(), 0, 2)).hasSize(2);
            assertThat(titulos.publicados(tenant.id(), 2, 2)).hasSize(1);
            assertThat(titulos.publicados(tenant.id(), 9, 2)).isEmpty();
        }

        @Test
        @DisplayName("revisao de direitos enxerga publicado e bloqueado, nao rascunho")
        void sujeitosARevisao() {
            var licenca = licencaVigente();
            var noAr = Titulo.criarFilme(tenant.id(), "No ar", ClassificacaoIndicativa.LIVRE,
                    Duration.ofMinutes(90)).valorOuFalha();
            noAr.definirVideoDoFilme("acervo/a");
            noAr.publicar(licenca, AGORA);
            titulos.salvar(noAr);

            var rascunho = Titulo.criarFilme(tenant.id(), "Rascunho", ClassificacaoIndicativa.LIVRE,
                    Duration.ofMinutes(90)).valorOuFalha();
            titulos.salvar(rascunho);

            assertThat(titulos.sujeitosARevisaoDeDireitos(tenant.id())).hasSize(1);
            assertThat(titulos.porLicenca(tenant.id(), licenca.id())).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Comercial")
    class DoComercial {

        private Plano plano() {
            var plano = Plano.criar(tenant.id(), "Familia", Dinheiro.reais(2490),
                    Periodicidade.MENSAL, 2, Qualidade.FULL_HD).valorOuFalha();
            plano.definirDescricao("2 telas");
            plano.definirDiasDeTeste(7);
            return planos.salvar(plano);
        }

        private Usuario assinante() {
            var usuario = Usuario.criar(tenant.id(), new Email("a@exemplo.com"), "hash", "Maria",
                    Set.of(Papel.ASSINANTE), AGORA).valorOuFalha();
            return usuarios.salvar(usuario);
        }

        @Test
        @DisplayName("plano preserva preco em centavos e teto de qualidade")
        void planoVaiEvolta() {
            var salvo = plano();

            var lido = planos.porId(tenant.id(), salvo.id()).orElseThrow();

            assertThat(lido.preco().centavos()).isEqualTo(2490);
            assertThat(lido.preco().formatado()).isEqualTo("R$ 24,90");
            assertThat(lido.qualidadeMaxima()).isEqualTo(Qualidade.FULL_HD);
            assertThat(lido.diasDeTeste()).isEqualTo(7);
            assertThat(planos.ativosDoTenant(tenant.id())).hasSize(1);

            lido.desativar();
            planos.salvar(lido);

            assertThat(planos.ativosDoTenant(tenant.id())).isEmpty();
        }

        @Test
        @DisplayName("cupom e achado pelo codigo em qualquer caixa")
        void cupomPorCodigo() {
            cupons.salvar(Cupom.criar(tenant.id(), "metade", 50, null, 10).valorOuFalha());

            assertThat(cupons.porCodigo(tenant.id(), "METADE")).isPresent();
            assertThat(cupons.porCodigo(tenant.id(), "metade")).isPresent();
            assertThat(cupons.porCodigo(tenant.id(), "outro")).isEmpty();
        }

        @Test
        @DisplayName("assinatura grava a linha do tempo sem duplicar evento")
        void assinaturaComEventos() {
            var plano = plano();
            var usuario = assinante();
            var assinatura = Assinatura.abrir(tenant.id(), usuario.id(), plano, AGORA)
                    .valorOuFalha();
            assinatura.vincularAoGateway("ref-123");
            assinaturas.salvar(assinatura);

            assinatura.confirmarPagamento(plano, AGORA.plus(Duration.ofDays(7)));
            assinaturas.salvar(assinatura);
            assinaturas.salvar(assinatura);

            var eventos = new JdbcTemplate(fonte).queryForObject(
                    "select count(*) from assinatura_eventos where assinatura_id = ?",
                    Integer.class, assinatura.id());
            assertThat(eventos).isEqualTo(assinatura.eventos().size());

            var lida = assinaturas.porReferenciaNoGateway("ref-123").orElseThrow();
            assertThat(lida.id()).isEqualTo(assinatura.id());
            assertThat(lida.fimDoCicloAtual()).isNotNull();
        }

        @Test
        @DisplayName("a assinatura vigente e a que ainda nao encerrou")
        void vigenteIgnoraEncerrada() {
            var plano = plano();
            var usuario = assinante();

            var antiga = Assinatura.abrir(tenant.id(), usuario.id(), plano,
                    AGORA.minus(Duration.ofDays(400))).valorOuFalha();
            antiga.aplicarPassagemDoTempo(AGORA.minus(Duration.ofDays(300)));
            assinaturas.salvar(antiga);

            var atual = Assinatura.abrir(tenant.id(), usuario.id(), plano, AGORA).valorOuFalha();
            assinaturas.salvar(atual);

            assertThat(assinaturas.vigenteDoUsuario(tenant.id(), usuario.id()).orElseThrow().id())
                    .isEqualTo(atual.id());
        }
    }

    @Nested
    @DisplayName("Exibicao")
    class DaExibicao {

        @Test
        @DisplayName("canal preserva fonte e o motivo de estar fora do ar")
        void canalVaiEvolta() {
            var licenca = Licenca.cadastrar(tenant.id(), "Emissora", "CT-CANAL",
                    Set.of(Territorio.BRASIL), JanelaDeLicenca.aPartirDe(
                            AGORA.minus(Duration.ofDays(1))),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            licenca.anexarComprovacao("arquivo://canal.pdf");
            licencas.salvar(licenca);

            var canal = CanalAoVivo.cadastrar(tenant.id(), "Serra TV", 10,
                    ClassificacaoIndicativa.LIVRE).valorOuFalha();
            canal.definirFonte("https://origem.exemplo.com/serra.m3u8");
            canal.definirLogo("logo.png");
            canal.colocarNoAr(licenca, AGORA);
            canais.salvar(canal);

            assertThat(canais.noAr(tenant.id())).hasSize(1);
            assertThat(canais.porLicenca(tenant.id(), licenca.id())).hasSize(1);

            canal.tirarDoAr("manutencao da emissora");
            canais.salvar(canal);

            var lido = canais.porId(tenant.id(), canal.id()).orElseThrow();
            assertThat(lido.noAr()).isFalse();
            assertThat(lido.bloqueadoPorDireito()).isFalse();
            assertThat(lido.motivoDoBloqueio()).isEqualTo("manutencao da emissora");
            assertThat(canais.noAr(tenant.id())).isEmpty();
            assertThat(canais.doTenant(tenant.id())).hasSize(1);
        }

        @Test
        @DisplayName("grade devolve so o que cruza a janela consultada")
        void gradePorJanela() {
            var canal = CanalAoVivo.cadastrar(tenant.id(), "Serra TV", 10,
                    ClassificacaoIndicativa.LIVRE).valorOuFalha();
            canal.definirFonte("https://origem.exemplo.com/serra.m3u8");
            canais.salvar(canal);

            epg.salvarTodos(List.of(
                    ProgramaEpg.criar(tenant.id(), canal.id(), "Jornal", AGORA,
                            AGORA.plus(Duration.ofHours(1)), ClassificacaoIndicativa.LIVRE)
                            .valorOuFalha(),
                    ProgramaEpg.criar(tenant.id(), canal.id(), "Filme",
                            AGORA.plus(Duration.ofHours(1)), AGORA.plus(Duration.ofHours(3)),
                            ClassificacaoIndicativa.DOZE_ANOS).valorOuFalha(),
                    ProgramaEpg.criar(tenant.id(), canal.id(), "Madrugada",
                            AGORA.plus(Duration.ofHours(6)), AGORA.plus(Duration.ofHours(9)),
                            ClassificacaoIndicativa.LIVRE).valorOuFalha()));

            var janela = epg.doCanalEntre(tenant.id(), canal.id(), AGORA,
                    AGORA.plus(Duration.ofHours(4)));

            assertThat(janela).extracting(ProgramaEpg::titulo).containsExactly("Jornal", "Filme");
            assertThat(ProgramaEpg.agora(janela, AGORA.plus(Duration.ofMinutes(30)))
                    .orElseThrow().titulo()).isEqualTo("Jornal");
        }

        @Test
        @DisplayName("conta so as sessoes vivas e fecha as abandonadas")
        void contagemDeSessoes() {
            var usuario = Usuario.criar(tenant.id(), new Email("a@exemplo.com"), "hash", "Maria",
                    Set.of(Papel.ASSINANTE), AGORA).valorOuFalha();
            usuarios.salvar(usuario);

            var viva = sessao(usuario.id(), "aparelho-1", AGORA);
            var abandonada = sessao(usuario.id(), "aparelho-2", AGORA.minus(Duration.ofMinutes(30)));
            sessoes.salvar(viva);
            sessoes.salvar(abandonada);

            assertThat(sessoes.abertasDoUsuario(tenant.id(), usuario.id(), AGORA)).isEqualTo(1);

            int fechadas = sessoes.fecharAbandonadas(AGORA.minus(Duration.ofMinutes(2)));

            assertThat(fechadas).isEqualTo(1);
            assertThat(sessoes.porId(abandonada.id()).orElseThrow().fechadaEm()).isNotNull();
            assertThat(sessoes.porId(viva.id()).orElseThrow().fechadaEm()).isNull();
        }

        private SessaoDeReproducao sessao(UUID usuarioId, String aparelho, Instant quando) {
            var autorizacao = new Autorizacao(UUID.randomUUID(), tenant.id(), null, null,
                    "acervo/x", Qualidade.HD, quando.plusSeconds(300), null);
            return SessaoDeReproducao.abrir(autorizacao, usuarioId, aparelho, quando);
        }

        @Test
        @DisplayName("auditoria grava e le os detalhes em jsonb")
        void auditoriaComDetalhes() {
            auditoria.registrar(RegistroDeAuditoria.de(tenant.id(), null, "sistema",
                    AcaoAuditavel.TITULO_PUBLICADO, "titulo", "abc", "203.0.113.10",
                    Map.of("contrato", "CT-2026-001",
                            "observacao", "com \"aspas\" e barra \\ dentro"),
                    AGORA));

            var lidos = auditoria.doTenant(tenant.id(), AGORA.minusSeconds(1),
                    AGORA.plusSeconds(1), 10);

            assertThat(lidos).hasSize(1);
            assertThat(lidos.get(0).detalhes())
                    .containsEntry("contrato", "CT-2026-001")
                    .containsEntry("observacao", "com \"aspas\" e barra \\ dentro");
            assertThat(lidos.get(0).enderecoIp()).isEqualTo("203.0.113.10");
        }

        @Test
        @DisplayName("auditoria sem detalhe grava jsonb vazio")
        void auditoriaSemDetalhe() {
            auditoria.registrar(RegistroDeAuditoria.de(tenant.id(), null, "sistema",
                    AcaoAuditavel.LOGIN_OK, "usuario", "abc", null, Map.of(), AGORA));

            assertThat(auditoria.doTenant(tenant.id(), AGORA.minusSeconds(1), AGORA.plusSeconds(1), 10)
                    .get(0).detalhes()).isEmpty();
        }
    }
}
