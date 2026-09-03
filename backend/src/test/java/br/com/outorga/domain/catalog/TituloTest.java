package br.com.outorga.domain.catalog;

import br.com.outorga.domain.rights.JanelaDeLicenca;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que este teste protege e a promessa central do produto: não existe título
 * no ar sem licença vigente, e o sistema tira do ar sozinho quando ela cai.
 */
@DisplayName("Título")
class TituloTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-24T12:00:00Z");

    private static Licenca licencaVigente() {
        return licencaAte(AGORA.plus(Duration.ofDays(30)));
    }

    private static Licenca licencaAte(Instant fim) {
        var licenca = Licenca.cadastrar(TENANT, "Produtora Serra", "CT-2026-001",
                Set.of(Territorio.BRASIL),
                new JanelaDeLicenca(AGORA.minus(Duration.ofDays(10)), fim),
                Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
        licenca.anexarComprovacao("s3://contrato.pdf");
        return licenca;
    }

    private static Titulo filmeComVideo() {
        var filme = Titulo.criarFilme(TENANT, "Estrada de Terra", ClassificacaoIndicativa.DOZE_ANOS,
                Duration.ofMinutes(96)).valorOuFalha();
        filme.definirVideoDoFilme("acervo/estrada-de-terra");
        return filme;
    }

    @Nested
    @DisplayName("ao publicar")
    class AoPublicar {

        @Test
        @DisplayName("pública com licença vigente e vídeo pronto")
        void publicaComLicencaVigente() {
            var filme = filmeComVideo();
            var licenca = licencaVigente();

            var resultado = filme.publicar(licenca, AGORA);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(filme.status()).isEqualTo(StatusDePublicacao.PUBLICADO);
            assertThat(filme.licencaId()).isEqualTo(licenca.id());
            assertThat(filme.noAr()).isTrue();
        }

        @Test
        @DisplayName("recusa sem licença")
        void recusaSemLicenca() {
            var resultado = filmeComVideo().publicar(null, AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("PUBLICACAO_SEM_LICENCA");
        }

        @Test
        @DisplayName("recusa licença ainda sem comprovacao")
        void recusaLicencaEmRascunho() {
            var rascunho = Licenca.cadastrar(TENANT, "Produtora", "CT-9", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA.minus(Duration.ofDays(1))),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();

            var resultado = filmeComVideo().publicar(rascunho, AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LICENCA_NAO_VIGENTE");
        }

        @Test
        @DisplayName("recusa licença já vencida")
        void recusaLicencaVencida() {
            var vencida = licencaAte(AGORA.minus(Duration.ofDays(1)));

            var resultado = filmeComVideo().publicar(vencida, AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LICENCA_NAO_VIGENTE");
        }

        @Test
        @DisplayName("recusa licença de outro cliente")
        void recusaLicencaDeOutroTenant() {
            var deOutro = Licenca.cadastrar(UUID.randomUUID(), "Produtora", "CT-8",
                    Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA.minus(Duration.ofDays(1))),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            deOutro.anexarComprovacao("s3://outro.pdf");

            var resultado = filmeComVideo().publicar(deOutro, AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LICENCA_DE_OUTRO_TENANT");
        }

        @Test
        @DisplayName("recusa filme sem vídeo")
        void recusaFilmeSemVideo() {
            var filme = Titulo.criarFilme(TENANT, "Sem arquivo", ClassificacaoIndicativa.LIVRE,
                    Duration.ofMinutes(80)).valorOuFalha();

            var resultado = filme.publicar(licencaVigente(), AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("TITULO_SEM_VIDEO");
        }

        @Test
        @DisplayName("recusa série sem nenhum episódio reproduzivel")
        void recusaSerieVazia() {
            var serie = Titulo.criarSerie(TENANT, "Cerrado", ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();
            var temporada = Temporada.criar(1, "Primeira temporada").valorOuFalha();
            temporada.adicionar(Episodio.criar(1, "Piloto", Duration.ofMinutes(42), null)
                    .valorOuFalha());
            serie.adicionarTemporada(temporada);

            var resultado = serie.publicar(licencaVigente(), AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("TITULO_SEM_VIDEO");
        }

        @Test
        @DisplayName("pública série com ao menos um episódio pronto")
        void publicaSerieComUmEpisodio() {
            var serie = Titulo.criarSerie(TENANT, "Cerrado", ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();
            var temporada = Temporada.criar(1, null).valorOuFalha();
            temporada.adicionar(Episodio.criar(1, "Piloto", Duration.ofMinutes(42),
                    "acervo/cerrado/s1e1").valorOuFalha());
            temporada.adicionar(Episodio.criar(2, "A cheia", Duration.ofMinutes(44), null)
                    .valorOuFalha());
            serie.adicionarTemporada(temporada);

            assertThat(serie.publicar(licencaVigente(), AGORA).sucesso()).isTrue();
            assertThat(serie.localizarEpisodio(1, 1)).isPresent();
            assertThat(serie.localizarEpisodio(1, 2).orElseThrow().reproduzivel()).isFalse();
        }
    }

    @Nested
    @DisplayName("na revisao de direitos")
    class NaRevisaoDeDireitos {

        @Test
        @DisplayName("bloqueia título no ar quando a janela vence")
        void bloqueiaQuandoVence() {
            var filme = filmeComVideo();
            var licenca = licencaAte(AGORA.plus(Duration.ofDays(1)));
            filme.publicar(licenca, AGORA);

            boolean mudou = filme.revisarDireitos(licenca, AGORA.plus(Duration.ofDays(2)));

            assertThat(mudou).isTrue();
            assertThat(filme.status()).isEqualTo(StatusDePublicacao.BLOQUEADO_POR_DIREITO);
            assertThat(filme.motivoDoBloqueio()).isEqualTo("Janela de licenciamento vencida");
            assertThat(filme.noAr()).isFalse();
        }

        @Test
        @DisplayName("bloqueia quando a licença e rescindida")
        void bloqueiaQuandoRescinde() {
            var filme = filmeComVideo();
            var licenca = licencaVigente();
            filme.publicar(licenca, AGORA);
            licenca.rescindir("distrato");

            boolean mudou = filme.revisarDireitos(licenca, AGORA);

            assertThat(mudou).isTrue();
            assertThat(filme.motivoDoBloqueio()).isEqualTo("Licença rescindida");
        }

        @Test
        @DisplayName("bloqueia quando a licença vinculada some")
        void bloqueiaQuandoLicencaSome() {
            var filme = filmeComVideo();
            filme.publicar(licencaVigente(), AGORA);

            boolean mudou = filme.revisarDireitos(null, AGORA);

            assertThat(mudou).isTrue();
            assertThat(filme.motivoDoBloqueio()).isEqualTo("Licença vinculada não encontrada");
        }

        @Test
        @DisplayName("devolve ao ar quando a licença volta a vigorar")
        void devolveAoArQuandoVolta() {
            var filme = filmeComVideo();
            var licenca = licencaAte(AGORA.plus(Duration.ofDays(1)));
            filme.publicar(licenca, AGORA);
            filme.revisarDireitos(licenca, AGORA.plus(Duration.ofDays(2)));

            var renovada = licencaAte(AGORA.plus(Duration.ofDays(400)));
            boolean mudou = filme.revisarDireitos(renovada, AGORA.plus(Duration.ofDays(3)));

            assertThat(mudou).isTrue();
            assertThat(filme.status()).isEqualTo(StatusDePublicacao.PUBLICADO);
            assertThat(filme.motivoDoBloqueio()).isNull();
        }

        @Test
        @DisplayName("não mexe em título que o operador despublicou de proposito")
        void naoMexeEmDespublicado() {
            var filme = filmeComVideo();
            var licenca = licencaVigente();
            filme.publicar(licenca, AGORA);
            filme.despublicar("saiu da grade");

            boolean mudou = filme.revisarDireitos(licenca, AGORA);

            assertThat(mudou).isFalse();
            assertThat(filme.status()).isEqualTo(StatusDePublicacao.DESPUBLICADO);
        }

        @Test
        @DisplayName("revisao sem mudança não reporta alteracao")
        void semMudancaNaoReporta() {
            var filme = filmeComVideo();
            var licenca = licencaVigente();
            filme.publicar(licenca, AGORA);

            assertThat(filme.revisarDireitos(licenca, AGORA.plus(Duration.ofDays(1)))).isFalse();
        }
    }

    @Nested
    @DisplayName("no controle parental")
    class NoControleParental {

        @Test
        @DisplayName("título de 12 anos não aparece em perfil de 10")
        void escondeAcimaDoTeto() {
            assertThat(filmeComVideo().visivelPara(ClassificacaoIndicativa.DEZ_ANOS)).isFalse();
        }

        @Test
        @DisplayName("título de 12 anos aparece em perfil de 16")
        void mostraDentroDoTeto() {
            assertThat(filmeComVideo().visivelPara(ClassificacaoIndicativa.DEZESSEIS_ANOS)).isTrue();
        }
    }

    @Nested
    @DisplayName("na montagem")
    class NaMontagem {

        @Test
        @DisplayName("recusa filme sem duração")
        void recusaFilmeSemDuracao() {
            var resultado = Titulo.criarFilme(TENANT, "Nada", ClassificacaoIndicativa.LIVRE, null);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("TITULO_SEM_DURACAO");
        }

        @Test
        @DisplayName("recusa temporada em filme")
        void recusaTemporadaEmFilme() {
            var resultado = filmeComVideo()
                    .adicionarTemporada(Temporada.criar(1, null).valorOuFalha());

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("TEMPORADA_EM_FILME");
        }

        @Test
        @DisplayName("recusa temporada repetida")
        void recusaTemporadaRepetida() {
            var serie = Titulo.criarSerie(TENANT, "Cerrado", ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();
            serie.adicionarTemporada(Temporada.criar(1, null).valorOuFalha());

            var resultado = serie.adicionarTemporada(Temporada.criar(1, null).valorOuFalha());

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("TEMPORADA_DUPLICADA");
        }

        @Test
        @DisplayName("recusa vídeo direto em série")
        void recusaVideoEmSerie() {
            var serie = Titulo.criarSerie(TENANT, "Cerrado", ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();

            assertThat(serie.definirVideoDoFilme("x").falha().orElseThrow().codigo())
                    .isEqualTo("VIDEO_DIRETO_EM_SERIE");
        }

        @Test
        @DisplayName("genero e normalizado para minusculo e sem repeticao")
        void normalizaGenero() {
            var filme = filmeComVideo();
            filme.adicionarGenero("Drama");
            filme.adicionarGenero("  drama ");
            filme.adicionarGenero("  ");

            assertThat(filme.generos()).containsExactly("drama");
        }

        @Test
        @DisplayName("despublicar só vale para título no ar")
        void despublicarSoNoAr() {
            assertThat(filmeComVideo().despublicar("motivo").falha().orElseThrow().codigo())
                    .isEqualTo("TITULO_NAO_PUBLICADO");
        }
    }
}
