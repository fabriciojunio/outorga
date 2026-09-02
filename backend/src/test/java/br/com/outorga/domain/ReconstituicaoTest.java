package br.com.outorga.domain;

import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Cupom;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.EventoDaAssinatura;
import br.com.outorga.domain.billing.Periodicidade;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.billing.StatusDaAssinatura;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.catalog.Episodio;
import br.com.outorga.domain.catalog.StatusDePublicacao;
import br.com.outorga.domain.catalog.Temporada;
import br.com.outorga.domain.catalog.TipoDeTitulo;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Dispositivo;
import br.com.outorga.domain.identity.Email;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.identity.Usuario;
import br.com.outorga.domain.live.CanalAoVivo;
import br.com.outorga.domain.playback.Autorizacao;
import br.com.outorga.domain.playback.SessaoDeReproducao;
import br.com.outorga.domain.rights.JanelaDeLicenca;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.rights.StatusDaLicenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.domain.tenant.Marca;
import br.com.outorga.domain.tenant.StatusDoTenant;
import br.com.outorga.domain.tenant.Tenant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ida e volta pelo caminho de reconstituicao, que e por onde toda entidade
 * passa quando vem do banco.
 *
 * Nao e teste de getter por teste de getter: um campo esquecido no
 * {@code reconstituir} some silenciosamente na primeira releitura, e o sintoma
 * aparece longe daqui, como um titulo que "perdeu" a licenca ou um assinante
 * que voltou a ser inadimplente do nada.
 */
@DisplayName("Reconstituicao a partir do banco")
class ReconstituicaoTest {

    private static final Instant AGORA = Instant.parse("2026-08-24T12:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();

    @Nested
    @DisplayName("Catalogo")
    class DoCatalogo {

        @Test
        @DisplayName("serie inteira volta com temporadas, episodios e estado")
        void serieCompleta() {
            var id = UUID.randomUUID();
            var licencaId = UUID.randomUUID();
            var episodio = Episodio.reconstituir(UUID.randomUUID(), 1, "Piloto", "sinopse do ep",
                    Duration.ofMinutes(42), "acervo/s1e1");
            var temporada = Temporada.reconstituir(UUID.randomUUID(), 1, "Primeira", List.of(episodio));

            var serie = Titulo.reconstituir(id, TENANT, TipoDeTitulo.SERIE, "Cerrado", "sinopse",
                    2025, ClassificacaoIndicativa.QUATORZE_ANOS, null, null, "capa.jpg",
                    Set.of("drama"), List.of(temporada), licencaId, StatusDePublicacao.PUBLICADO,
                    AGORA, null);

            assertThat(serie.id()).isEqualTo(id);
            assertThat(serie.tenantId()).isEqualTo(TENANT);
            assertThat(serie.sinopse()).isEqualTo("sinopse");
            assertThat(serie.anoDeProducao()).isEqualTo(2025);
            assertThat(serie.capaUri()).isEqualTo("capa.jpg");
            assertThat(serie.generos()).containsExactly("drama");
            assertThat(serie.licencaId()).isEqualTo(licencaId);
            assertThat(serie.publicadoEm()).isEqualTo(AGORA);
            assertThat(serie.noAr()).isTrue();
            assertThat(serie.temporadas()).hasSize(1);
            assertThat(serie.localizarEpisodio(1, 1)).isPresent();
            assertThat(serie.localizarEpisodio(2, 1)).isEmpty();
            assertThat(serie.localizarEpisodio(1, 9)).isEmpty();
        }

        @Test
        @DisplayName("titulo bloqueado volta com o motivo do bloqueio")
        void bloqueadoMantemMotivo() {
            var filme = Titulo.reconstituir(UUID.randomUUID(), TENANT, TipoDeTitulo.FILME, "X", null,
                    null, ClassificacaoIndicativa.LIVRE, Duration.ofMinutes(90), "acervo/x", null,
                    Set.of(), List.of(), UUID.randomUUID(),
                    StatusDePublicacao.BLOQUEADO_POR_DIREITO, AGORA, "Janela vencida");

            assertThat(filme.motivoDoBloqueio()).isEqualTo("Janela vencida");
            assertThat(filme.duracao()).isEqualTo(Duration.ofMinutes(90));
            assertThat(filme.referenciaDoVideo()).isEqualTo("acervo/x");
            assertThat(filme.noAr()).isFalse();
        }

        @Test
        @DisplayName("temporada ordena episodios pelo numero, venha o banco como vier")
        void temporadaOrdena() {
            var terceiro = Episodio.reconstituir(UUID.randomUUID(), 3, "C", null,
                    Duration.ofMinutes(40), "c");
            var primeiro = Episodio.reconstituir(UUID.randomUUID(), 1, "A", null,
                    Duration.ofMinutes(40), "a");

            var temporada = Temporada.reconstituir(UUID.randomUUID(), 1, "T1",
                    List.of(terceiro, primeiro));

            assertThat(temporada.episodios()).extracting(Episodio::numero).containsExactly(1, 3);
            assertThat(temporada.temEpisodioReproduzivel()).isTrue();
        }

        @Test
        @DisplayName("episodio recusa numero, titulo e duracao invalidos")
        void episodioValida() {
            assertThat(Episodio.criar(0, "A", Duration.ofMinutes(1), null).falha().orElseThrow()
                    .codigo()).isEqualTo("EPISODIO_NUMERO_INVALIDO");
            assertThat(Episodio.criar(1, " ", Duration.ofMinutes(1), null).falha().orElseThrow()
                    .codigo()).isEqualTo("EPISODIO_SEM_TITULO");
            assertThat(Episodio.criar(1, "A", Duration.ZERO, null).falha().orElseThrow()
                    .codigo()).isEqualTo("EPISODIO_SEM_DURACAO");
        }

        @Test
        @DisplayName("episodio ganha video e sinopse depois de criado")
        void episodioRecebeVideo() {
            var episodio = Episodio.criar(1, "Piloto", Duration.ofMinutes(42), null).valorOuFalha();
            assertThat(episodio.reproduzivel()).isFalse();

            episodio.definirReferenciaDoVideo("acervo/s1e1");
            episodio.definirSinopse("o comeco de tudo");

            assertThat(episodio.reproduzivel()).isTrue();
            assertThat(episodio.sinopse()).isEqualTo("o comeco de tudo");
            assertThat(episodio.id()).isNotNull();
        }

        @Test
        @DisplayName("temporada recusa episodio com numero repetido")
        void temporadaRecusaDuplicado() {
            var temporada = Temporada.criar(1, null).valorOuFalha();
            temporada.adicionar(Episodio.criar(1, "A", Duration.ofMinutes(40), "a").valorOuFalha());

            var repetido = temporada.adicionar(
                    Episodio.criar(1, "B", Duration.ofMinutes(40), "b").valorOuFalha());

            assertThat(repetido.falha().orElseThrow().codigo()).isEqualTo("EPISODIO_DUPLICADO");
            assertThat(temporada.titulo()).isEqualTo("Temporada 1");
        }

        @Test
        @DisplayName("temporada recusa numero abaixo de um")
        void temporadaRecusaNumero() {
            assertThat(Temporada.criar(0, "X").falha().orElseThrow().codigo())
                    .isEqualTo("TEMPORADA_NUMERO_INVALIDO");
        }
    }

    @Nested
    @DisplayName("Identidade")
    class DaIdentidade {

        @Test
        @DisplayName("usuario volta com bloqueio, ultimo acesso e anonimizacao")
        void usuarioCompleto() {
            var id = UUID.randomUUID();
            var bloqueadoAte = AGORA.plus(Duration.ofMinutes(15));

            var usuario = Usuario.reconstituir(id, TENANT, new Email("a@exemplo.com"), "hash",
                    "Nome", Set.of(Papel.DONO), true, 3, bloqueadoAte, AGORA,
                    AGORA.minus(Duration.ofDays(30)), null);

            assertThat(usuario.id()).isEqualTo(id);
            assertThat(usuario.tentativasSeguidas()).isEqualTo(3);
            assertThat(usuario.bloqueadoAte()).isEqualTo(bloqueadoAte);
            assertThat(usuario.ultimoAcesso()).isEqualTo(AGORA);
            assertThat(usuario.criadoEm()).isEqualTo(AGORA.minus(Duration.ofDays(30)));
            assertThat(usuario.anonimizadoEm()).isNull();
            assertThat(usuario.senhaHash()).isEqualTo("hash");
            assertThat(usuario.tem(Papel.DONO)).isTrue();
            assertThat(usuario.podeMexerEmCobranca()).isTrue();
        }

        @Test
        @DisplayName("reativar limpa bloqueio e contador")
        void reativarLimpa() {
            var usuario = Usuario.reconstituir(UUID.randomUUID(), TENANT,
                    new Email("a@exemplo.com"), "hash", "Nome", Set.of(Papel.ASSINANTE), false, 5,
                    AGORA.plus(Duration.ofHours(1)), null, AGORA, null);

            usuario.reativar();

            assertThat(usuario.ativo()).isTrue();
            assertThat(usuario.tentativasSeguidas()).isZero();
            assertThat(usuario.estaBloqueado(AGORA)).isFalse();
        }

        @Test
        @DisplayName("trocar senha zera o bloqueio e recusa senha vazia")
        void trocaDeSenha() {
            var usuario = Usuario.reconstituir(UUID.randomUUID(), TENANT,
                    new Email("a@exemplo.com"), "hash", "Nome", Set.of(Papel.ASSINANTE), true, 4,
                    AGORA.plus(Duration.ofHours(1)), null, AGORA, null);

            assertThat(usuario.trocarSenha("  ").falha().orElseThrow().codigo())
                    .isEqualTo("SENHA_OBRIGATORIA");

            usuario.trocarSenha("novo-hash");

            assertThat(usuario.senhaHash()).isEqualTo("novo-hash");
            assertThat(usuario.estaBloqueado(AGORA)).isFalse();
        }

        @Test
        @DisplayName("perfil volta com pin, avatar e teto")
        void perfilCompleto() {
            var id = UUID.randomUUID();

            var perfil = Perfil.reconstituir(id, USUARIO, "Maria",
                    ClassificacaoIndicativa.DEZESSEIS_ANOS, "hash-pin", false, "avatar-3.png");

            assertThat(perfil.id()).isEqualTo(id);
            assertThat(perfil.usuarioId()).isEqualTo(USUARIO);
            assertThat(perfil.pinHash()).isEqualTo("hash-pin");
            assertThat(perfil.avatar()).isEqualTo("avatar-3.png");
            assertThat(perfil.protegidoPorPin()).isTrue();
            assertThat(perfil.infantil()).isFalse();
        }

        @Test
        @DisplayName("perfil adulto aceita subir o teto")
        void perfilAjustaTeto() {
            var perfil = Perfil.criar(USUARIO, "Maria", ClassificacaoIndicativa.DOZE_ANOS, false, 0)
                    .valorOuFalha();

            assertThat(perfil.ajustarTeto(ClassificacaoIndicativa.DEZOITO_ANOS).sucesso()).isTrue();
            assertThat(perfil.tetoDeClassificacao()).isEqualTo(ClassificacaoIndicativa.DEZOITO_ANOS);
            assertThat(perfil.ajustarTeto(null).falha().orElseThrow().codigo())
                    .isEqualTo("TETO_INVALIDO");
        }

        @Test
        @DisplayName("perfil aceita avatar e recusa criacao sem nome ou sem conta")
        void perfilValida() {
            var perfil = Perfil.criar(USUARIO, "Maria", null, false, 0).valorOuFalha();
            perfil.definirAvatar("avatar-1.png");

            assertThat(perfil.avatar()).isEqualTo("avatar-1.png");
            assertThat(Perfil.criar(USUARIO, "  ", null, false, 0).falha().orElseThrow().codigo())
                    .isEqualTo("PERFIL_SEM_NOME");
            assertThat(Perfil.criar(null, "Maria", null, false, 0).falha().orElseThrow().codigo())
                    .isEqualTo("PERFIL_SEM_CONTA");
        }

        @Test
        @DisplayName("dispositivo volta com datas e marca uso novo")
        void dispositivoCompleto() {
            var id = UUID.randomUUID();
            var dispositivo = Dispositivo.reconstituir(id, USUARIO, "aparelho-1",
                    TipoDeDispositivo.ANDROID, "Celular", AGORA.minus(Duration.ofDays(10)), AGORA);

            assertThat(dispositivo.id()).isEqualTo(id);
            assertThat(dispositivo.registradoEm()).isEqualTo(AGORA.minus(Duration.ofDays(10)));
            assertThat(dispositivo.ultimoUso()).isEqualTo(AGORA);

            dispositivo.marcarUso(AGORA.plus(Duration.ofHours(2)));

            assertThat(dispositivo.ultimoUso()).isEqualTo(AGORA.plus(Duration.ofHours(2)));
        }

        @Test
        @DisplayName("dispositivo recusa registro sem conta e sem tipo")
        void dispositivoValida() {
            assertThat(Dispositivo.registrar(null, "id", TipoDeDispositivo.WEB, "x", AGORA)
                    .falha().orElseThrow().codigo()).isEqualTo("DISPOSITIVO_SEM_CONTA");
            assertThat(Dispositivo.registrar(USUARIO, "id", null, "x", AGORA)
                    .falha().orElseThrow().codigo()).isEqualTo("DISPOSITIVO_SEM_TIPO");
        }
    }

    @Nested
    @DisplayName("Comercial")
    class DoComercial {

        @Test
        @DisplayName("plano volta inteiro, inclusive descricao e dias de teste")
        void planoCompleto() {
            var id = UUID.randomUUID();

            var plano = Plano.reconstituir(id, TENANT, "Familia", "ate 2 telas",
                    Dinheiro.reais(2490), Periodicidade.ANUAL, 2, Qualidade.FULL_HD, 7, false);

            assertThat(plano.id()).isEqualTo(id);
            assertThat(plano.descricao()).isEqualTo("ate 2 telas");
            assertThat(plano.diasDeTeste()).isEqualTo(7);
            assertThat(plano.ativo()).isFalse();
            assertThat(plano.periodicidade()).isEqualTo(Periodicidade.ANUAL);
            assertThat(plano.qualidadeMaxima()).isEqualTo(Qualidade.FULL_HD);
            assertThat(plano.preco().centavos()).isEqualTo(2490);
        }

        @Test
        @DisplayName("plano recusa criacao incompleta")
        void planoValida() {
            assertThat(Plano.criar(null, "X", Dinheiro.reais(1), Periodicidade.MENSAL, 1,
                    Qualidade.HD).falha().orElseThrow().codigo()).isEqualTo("PLANO_SEM_TENANT");
            assertThat(Plano.criar(TENANT, " ", Dinheiro.reais(1), Periodicidade.MENSAL, 1,
                    Qualidade.HD).falha().orElseThrow().codigo()).isEqualTo("PLANO_SEM_NOME");
            assertThat(Plano.criar(TENANT, "X", null, Periodicidade.MENSAL, 1,
                    Qualidade.HD).falha().orElseThrow().codigo()).isEqualTo("PLANO_SEM_PRECO");
            assertThat(Plano.criar(TENANT, "X", Dinheiro.reais(1), null, 1,
                    Qualidade.HD).falha().orElseThrow().codigo()).isEqualTo("PLANO_INCOMPLETO");
        }

        @Test
        @DisplayName("dias de teste negativo vira zero")
        void diasDeTesteNaoFicaNegativo() {
            var plano = Plano.criar(TENANT, "X", Dinheiro.reais(100), Periodicidade.MENSAL, 1,
                    Qualidade.HD).valorOuFalha();

            plano.definirDiasDeTeste(-5);

            assertThat(plano.diasDeTeste()).isZero();
        }

        @Test
        @DisplayName("cupom volta com uso ja gasto e pode ser desativado")
        void cupomCompleto() {
            var id = UUID.randomUUID();
            var cupom = Cupom.reconstituir(id, TENANT, "METADE", 50, AGORA.plus(Duration.ofDays(30)),
                    10, 3, true);

            assertThat(cupom.id()).isEqualTo(id);
            assertThat(cupom.tenantId()).isEqualTo(TENANT);
            assertThat(cupom.usos()).isEqualTo(3);
            assertThat(cupom.usosMaximos()).isEqualTo(10);
            assertThat(cupom.validoAte()).isEqualTo(AGORA.plus(Duration.ofDays(30)));
            assertThat(cupom.resgatar(AGORA).sucesso()).isTrue();

            cupom.desativar();

            assertThat(cupom.ativo()).isFalse();
            assertThat(cupom.resgatar(AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("CUPOM_INATIVO");
        }

        @Test
        @DisplayName("cupom recusa criacao sem codigo ou sem limite de uso")
        void cupomValida() {
            assertThat(Cupom.criar(TENANT, " ", 10, null, 1).falha().orElseThrow().codigo())
                    .isEqualTo("CUPOM_SEM_CODIGO");
            assertThat(Cupom.criar(TENANT, "X", 10, null, 0).falha().orElseThrow().codigo())
                    .isEqualTo("CUPOM_SEM_USOS");
        }

        @Test
        @DisplayName("assinatura volta com eventos e datas preservados")
        void assinaturaCompleta() {
            var id = UUID.randomUUID();
            var evento = EventoDaAssinatura.de(id,
                    EventoDaAssinatura.TipoDeEvento.PAGAMENTO_CONFIRMADO, "ok", AGORA);

            var assinatura = Assinatura.reconstituir(id, TENANT, USUARIO, UUID.randomUUID(),
                    StatusDaAssinatura.ATIVA, AGORA.minus(Duration.ofDays(30)),
                    AGORA.plus(Duration.ofDays(10)), null, null, "ref-123", List.of(evento));

            assertThat(assinatura.id()).isEqualTo(id);
            assertThat(assinatura.usuarioId()).isEqualTo(USUARIO);
            assertThat(assinatura.referenciaNoGateway()).isEqualTo("ref-123");
            assertThat(assinatura.eventos()).hasSize(1);
            assertThat(assinatura.encerradaEm()).isNull();
            assertThat(assinatura.permiteAssistir(AGORA)).isTrue();
        }

        @Test
        @DisplayName("assinatura encerrada guarda a data e recusa cancelar de novo")
        void assinaturaEncerrada() {
            var assinatura = Assinatura.reconstituir(UUID.randomUUID(), TENANT, USUARIO,
                    UUID.randomUUID(), StatusDaAssinatura.ENCERRADA, AGORA.minus(Duration.ofDays(60)),
                    AGORA.minus(Duration.ofDays(30)), null, AGORA.minus(Duration.ofDays(30)),
                    null, List.of());

            assertThat(assinatura.encerradaEm()).isEqualTo(AGORA.minus(Duration.ofDays(30)));
            assertThat(assinatura.permiteAssistir(AGORA)).isFalse();
            assertThat(assinatura.aplicarPassagemDoTempo(AGORA)).isFalse();
            assertThat(assinatura.cancelar("x", AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("ASSINATURA_ENCERRADA");
            assertThat(assinatura.registrarFalhaDePagamento("x", AGORA).falha().orElseThrow()
                    .codigo()).isEqualTo("ASSINATURA_ENCERRADA");
        }

        @Test
        @DisplayName("assinatura recusa abertura incompleta e troca para plano inativo")
        void assinaturaValida() {
            assertThat(Assinatura.abrir(null, USUARIO, null, AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("ASSINATURA_INCOMPLETA");

            var plano = Plano.criar(TENANT, "X", Dinheiro.reais(100), Periodicidade.MENSAL, 1,
                    Qualidade.HD).valorOuFalha();
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
            var inativo = Plano.criar(TENANT, "Y", Dinheiro.reais(100), Periodicidade.MENSAL, 1,
                    Qualidade.HD).valorOuFalha();
            inativo.desativar();

            assertThat(assinatura.trocarPlano(inativo, AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("PLANO_INATIVO");
            assertThat(assinatura.trocarPlano(
                    Plano.criar(UUID.randomUUID(), "Z", Dinheiro.reais(100), Periodicidade.MENSAL, 1,
                            Qualidade.HD).valorOuFalha(), AGORA)
                    .falha().orElseThrow().codigo()).isEqualTo("PLANO_DE_OUTRO_TENANT");
        }

        @Test
        @DisplayName("periodicidade calcula o proximo vencimento")
        void periodicidadeCalcula() {
            assertThat(Periodicidade.ANUAL.proximoVencimento(AGORA))
                    .isEqualTo(AGORA.plus(Duration.ofDays(365)));
            assertThat(Periodicidade.TRIMESTRAL.dias()).isEqualTo(90);
            assertThat(Periodicidade.SEMESTRAL.dias()).isEqualTo(180);
        }

        @Test
        @DisplayName("qualidade maior cobre a menor")
        void qualidadeCobre() {
            assertThat(Qualidade.ULTRA_HD.cobre(Qualidade.SD)).isTrue();
            assertThat(Qualidade.SD.cobre(Qualidade.ULTRA_HD)).isFalse();
            assertThat(Qualidade.HD.alturaMaxima()).isEqualTo(720);
        }

        @Test
        @DisplayName("dinheiro multiplica e compara")
        void dinheiroMultiplicaEcompara() {
            assertThat(Dinheiro.reais(1000).vezes(3).centavos()).isEqualTo(3000);
            assertThat(Dinheiro.reais(1000).mais(Dinheiro.reais(490)).centavos()).isEqualTo(1490);
            assertThat(Dinheiro.reais(2490)).isGreaterThan(Dinheiro.reais(1490));
            assertThat(Dinheiro.reais(1490).emUnidades().toPlainString()).isEqualTo("14.90");
        }
    }

    @Nested
    @DisplayName("Cliente e canal")
    class DoClienteEcanal {

        @Test
        @DisplayName("tenant volta com dominio proprio e periodo de teste")
        void tenantCompleto() {
            var id = UUID.randomUUID();
            var marca = new Marca("Cine Serra", "logo.svg", "#112233", "#000000");

            var tenant = Tenant.reconstituir(id, "cineserra", "Cine Serra Ltda", "12345678000190",
                    "assista.cineserra.com.br", marca, StatusDoTenant.ATIVO,
                    AGORA.minus(Duration.ofDays(200)), AGORA.plus(Duration.ofDays(5)), null);

            assertThat(tenant.id()).isEqualTo(id);
            assertThat(tenant.documento()).isEqualTo("12345678000190");
            assertThat(tenant.dominioProprio()).isEqualTo("assista.cineserra.com.br");
            assertThat(tenant.marca().logoUri()).isEqualTo("logo.svg");
            assertThat(tenant.marca().corPrimaria()).isEqualTo("#112233");
            assertThat(tenant.criadoEm()).isEqualTo(AGORA.minus(Duration.ofDays(200)));
            assertThat(tenant.fimDoTeste()).isEqualTo(AGORA.plus(Duration.ofDays(5)));
            assertThat(tenant.aceitaTrafegoDeEspectador(AGORA)).isTrue();
        }

        @Test
        @DisplayName("definir dominio normaliza para minusculo")
        void dominioNormaliza() {
            var tenant = Tenant.abrir("cineserra", "Cine Serra", null, null, AGORA).valorOuFalha();

            tenant.definirDominioProprio("  Assista.CineSerra.COM.BR ");

            assertThat(tenant.dominioProprio()).isEqualTo("assista.cineserra.com.br");

            tenant.definirDominioProprio(null);

            assertThat(tenant.dominioProprio()).isNull();
        }

        @Test
        @DisplayName("trocar a marca reflete na identidade do aplicativo")
        void trocaMarca() {
            var tenant = Tenant.abrir("cineserra", "Cine Serra", null, null, AGORA).valorOuFalha();

            tenant.definirMarca(new Marca("Serra Play", "novo.svg", "#ff0000", "#111111"));

            assertThat(tenant.marca().nomeExibido()).isEqualTo("Serra Play");
            assertThat(tenant.marca().corDeFundo()).isEqualTo("#111111");
        }

        @Test
        @DisplayName("tenant encerrado nao aceita suspensao nem trafego")
        void encerradoNaoAceita() {
            var tenant = Tenant.abrir("cineserra", "Cine Serra", null, null, AGORA).valorOuFalha();
            tenant.encerrar();

            assertThat(tenant.suspender("x").falha().orElseThrow().codigo())
                    .isEqualTo("TENANT_ENCERRADO");
            assertThat(tenant.aceitaTrafegoDeEspectador(AGORA)).isFalse();
        }

        @Test
        @DisplayName("tenant recusa nome vazio")
        void tenantRecusaNomeVazio() {
            assertThat(Tenant.abrir("cineserra", "  ", null, null, AGORA).falha().orElseThrow()
                    .codigo()).isEqualTo("TENANT_SEM_NOME");
        }

        @Test
        @DisplayName("canal volta com fonte, logo e estado de bloqueio")
        void canalCompleto() {
            var id = UUID.randomUUID();
            var licencaId = UUID.randomUUID();

            var canal = CanalAoVivo.reconstituir(id, TENANT, "Serra TV", "logo.png", 10,
                    "https://origem/serra.m3u8", ClassificacaoIndicativa.DEZ_ANOS, licencaId,
                    false, "Licenca do canal sem vigencia", true);

            assertThat(canal.id()).isEqualTo(id);
            assertThat(canal.logoUri()).isEqualTo("logo.png");
            assertThat(canal.numero()).isEqualTo(10);
            assertThat(canal.urlDaFonte()).isEqualTo("https://origem/serra.m3u8");
            assertThat(canal.licencaId()).isEqualTo(licencaId);
            assertThat(canal.bloqueadoPorDireito()).isTrue();
            assertThat(canal.visivelPara(ClassificacaoIndicativa.LIVRE)).isFalse();
            assertThat(canal.visivelPara(ClassificacaoIndicativa.DOZE_ANOS)).isTrue();
        }

        @Test
        @DisplayName("canal recusa cadastro incompleto e fonte vazia")
        void canalValida() {
            assertThat(CanalAoVivo.cadastrar(null, "X", 1, ClassificacaoIndicativa.LIVRE)
                    .falha().orElseThrow().codigo()).isEqualTo("CANAL_SEM_TENANT");
            assertThat(CanalAoVivo.cadastrar(TENANT, " ", 1, ClassificacaoIndicativa.LIVRE)
                    .falha().orElseThrow().codigo()).isEqualTo("CANAL_SEM_NOME");
            assertThat(CanalAoVivo.cadastrar(TENANT, "X", 0, ClassificacaoIndicativa.LIVRE)
                    .falha().orElseThrow().codigo()).isEqualTo("CANAL_NUMERO_INVALIDO");
            assertThat(CanalAoVivo.cadastrar(TENANT, "X", 1, null)
                    .falha().orElseThrow().codigo()).isEqualTo("CANAL_SEM_CLASSIFICACAO");

            var canal = CanalAoVivo.cadastrar(TENANT, "X", 1, ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();
            canal.definirLogo("logo.png");

            assertThat(canal.definirFonte("  ").falha().orElseThrow().codigo())
                    .isEqualTo("FONTE_VAZIA");
            assertThat(canal.tirarDoAr("x").falha().orElseThrow().codigo())
                    .isEqualTo("CANAL_JA_FORA_DO_AR");
            assertThat(canal.logoUri()).isEqualTo("logo.png");
        }
    }

    @Nested
    @DisplayName("Direitos e sessao")
    class DosDireitosEsessao {

        @Test
        @DisplayName("licenca volta com comprovacao, status e observacao")
        void licencaCompleta() {
            var id = UUID.randomUUID();

            var licenca = Licenca.reconstituir(id, TENANT, "Produtora", "CT-1",
                    Set.of(Territorio.BRASIL),
                    new JanelaDeLicenca(AGORA.minus(Duration.ofDays(1)), null),
                    Set.of(TipoDeDispositivo.WEB), "s3://ct-1.pdf", StatusDaLicenca.VIGENTE,
                    "renovacao em negociacao");

            assertThat(licenca.id()).isEqualTo(id);
            assertThat(licenca.comprovacaoUri()).isEqualTo("s3://ct-1.pdf");
            assertThat(licenca.observacao()).isEqualTo("renovacao em negociacao");
            assertThat(licenca.vigenteEm(AGORA)).isTrue();
            assertThat(licenca.janela().indeterminada()).isTrue();
        }

        @Test
        @DisplayName("licenca recusa cadastro sem janela e sem dispositivo")
        void licencaValida() {
            assertThat(Licenca.cadastrar(TENANT, "T", "C", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of()).falha().orElseThrow().codigo())
                    .isEqualTo("LICENCA_SEM_DISPOSITIVO");
            assertThat(Licenca.cadastrar(TENANT, "T", "C", Set.of(Territorio.BRASIL), null,
                    Set.of(TipoDeDispositivo.WEB)).falha().orElseThrow().codigo())
                    .isEqualTo("LICENCA_SEM_JANELA");
            assertThat(Licenca.cadastrar(null, "T", "C", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of(TipoDeDispositivo.WEB))
                    .falha().orElseThrow().codigo()).isEqualTo("LICENCA_SEM_TENANT");
        }

        @Test
        @DisplayName("rescindir duas vezes e recusado")
        void rescindirDuasVezes() {
            var licenca = Licenca.cadastrar(TENANT, "T", "C", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            licenca.rescindir("motivo");

            assertThat(licenca.rescindir("de novo").falha().orElseThrow().codigo())
                    .isEqualTo("LICENCA_JA_RESCINDIDA");
            assertThat(licenca.observacao()).isEqualTo("motivo");
        }

        @Test
        @DisplayName("sessao volta com posicao e fechamento")
        void sessaoCompleta() {
            var id = UUID.randomUUID();
            var perfilId = UUID.randomUUID();
            var tituloId = UUID.randomUUID();

            var sessao = SessaoDeReproducao.reconstituir(id, TENANT, USUARIO, perfilId, tituloId,
                    "aparelho-1", AGORA.minus(Duration.ofHours(2)),
                    AGORA.minus(Duration.ofMinutes(30)), AGORA.minus(Duration.ofMinutes(30)), 3600);

            assertThat(sessao.id()).isEqualTo(id);
            assertThat(sessao.perfilId()).isEqualTo(perfilId);
            assertThat(sessao.tituloId()).isEqualTo(tituloId);
            assertThat(sessao.dispositivoId()).isEqualTo("aparelho-1");
            assertThat(sessao.abertaEm()).isEqualTo(AGORA.minus(Duration.ofHours(2)));
            assertThat(sessao.posicaoEmSegundos()).isEqualTo(3600);
            assertThat(sessao.viva(AGORA)).isFalse();
        }

        @Test
        @DisplayName("autorizacao carrega o que o player precisa")
        void autorizacaoCarrega() {
            var sessaoId = UUID.randomUUID();
            var autorizacao = new Autorizacao(sessaoId, TENANT, null, UUID.randomUUID(),
                    "acervo/x", Qualidade.HD, AGORA.plus(Duration.ofMinutes(5)), UUID.randomUUID());

            var sessao = SessaoDeReproducao.abrir(autorizacao, USUARIO, "aparelho-1", AGORA);

            assertThat(sessao.id()).isEqualTo(sessaoId);
            assertThat(sessao.usuarioId()).isEqualTo(USUARIO);
            assertThat(sessao.tenantId()).isEqualTo(TENANT);
        }
    }
}
