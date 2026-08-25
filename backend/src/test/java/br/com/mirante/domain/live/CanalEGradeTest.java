package br.com.mirante.domain.live;

import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.domain.rights.JanelaDeLicenca;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.domain.rights.Territorio;
import br.com.mirante.domain.rights.TipoDeDispositivo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Canal ao vivo e grade")
class CanalEGradeTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-24T20:00:00Z");

    private static Licenca licencaVigente() {
        var licenca = Licenca.cadastrar(TENANT, "Emissora Regional", "CT-CANAL-1",
                Set.of(Territorio.BRASIL),
                new JanelaDeLicenca(AGORA.minus(Duration.ofDays(1)), AGORA.plus(Duration.ofDays(90))),
                Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
        licenca.anexarComprovacao("s3://canal.pdf");
        return licenca;
    }

    private static CanalAoVivo canalComFonte() {
        var canal = CanalAoVivo.cadastrar(TENANT, "Serra TV", 10, ClassificacaoIndicativa.LIVRE)
                .valorOuFalha();
        canal.definirFonte("https://origem.exemplo.com/serra/index.m3u8");
        return canal;
    }

    @Nested
    @DisplayName("no cadastro da fonte")
    class NaFonte {

        @ParameterizedTest
        @ValueSource(strings = {"http://inseguro.com/x.m3u8", "ftp://a/b", "udp://239.0.0.1:1234"})
        @DisplayName("recusa fonte que nao trafega cifrada")
        void recusaFonteInsegura(String url) {
            var canal = CanalAoVivo.cadastrar(TENANT, "Serra TV", 10, ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();

            assertThat(canal.definirFonte(url).falha().orElseThrow().codigo())
                    .isEqualTo("FONTE_INSEGURA");
        }

        @ParameterizedTest
        @ValueSource(strings = {"https://a/b.m3u8", "rtmps://a/live", "srt://a:9000"})
        @DisplayName("aceita https, rtmps e srt")
        void aceitaFonteSegura(String url) {
            var canal = CanalAoVivo.cadastrar(TENANT, "Serra TV", 10, ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();

            assertThat(canal.definirFonte(url).sucesso()).isTrue();
        }
    }

    @Nested
    @DisplayName("ao entrar no ar")
    class AoEntrarNoAr {

        @Test
        @DisplayName("entra no ar com fonte e licenca vigente")
        void entraNoAr() {
            var canal = canalComFonte();

            assertThat(canal.colocarNoAr(licencaVigente(), AGORA).sucesso()).isTrue();
            assertThat(canal.noAr()).isTrue();
        }

        @Test
        @DisplayName("recusa canal sem fonte cadastrada")
        void recusaSemFonte() {
            var canal = CanalAoVivo.cadastrar(TENANT, "Serra TV", 10, ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();

            assertThat(canal.colocarNoAr(licencaVigente(), AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("CANAL_SEM_FONTE");
        }

        @Test
        @DisplayName("recusa licenca de outro cliente")
        void recusaLicencaDeOutro() {
            var deOutro = Licenca.cadastrar(UUID.randomUUID(), "Outra", "CT-2",
                    Set.of(Territorio.BRASIL), JanelaDeLicenca.aPartirDe(AGORA),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            deOutro.anexarComprovacao("s3://outro.pdf");

            assertThat(canalComFonte().colocarNoAr(deOutro, AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("LICENCA_INVALIDA");
        }

        @Test
        @DisplayName("sai do ar quando a licenca deixa de valer e volta quando renova")
        void saiEVolta() {
            var canal = canalComFonte();
            var licenca = licencaVigente();
            canal.colocarNoAr(licenca, AGORA);

            licenca.rescindir("distrato");
            assertThat(canal.revisarDireitos(licenca, AGORA)).isTrue();
            assertThat(canal.noAr()).isFalse();

            var renovada = licencaVigente();
            assertThat(canal.revisarDireitos(renovada, AGORA)).isTrue();
            assertThat(canal.noAr()).isTrue();
        }

        @Test
        @DisplayName("canal tirado do ar pelo operador nao volta sozinho")
        void tiradoManualmenteNaoVolta() {
            var canal = canalComFonte();
            var licenca = licencaVigente();
            canal.colocarNoAr(licenca, AGORA);
            canal.tirarDoAr("manutencao da emissora");

            assertThat(canal.revisarDireitos(licenca, AGORA)).isFalse();
            assertThat(canal.noAr()).isFalse();
        }
    }

    @Nested
    @DisplayName("na grade de programacao")
    class NaGrade {

        private ProgramaEpg programa(String nome, int deHoras, int ateHoras) {
            return ProgramaEpg.criar(TENANT, UUID.fromString("11111111-1111-1111-1111-111111111111"),
                    nome, AGORA.plus(Duration.ofHours(deHoras)),
                    AGORA.plus(Duration.ofHours(ateHoras)), ClassificacaoIndicativa.LIVRE)
                    .valorOuFalha();
        }

        @Test
        @DisplayName("recusa programa com fim antes do inicio")
        void recusaHorarioInvertido() {
            var resultado = ProgramaEpg.criar(TENANT, UUID.randomUUID(), "Jornal",
                    AGORA, AGORA.minus(Duration.ofHours(1)), ClassificacaoIndicativa.LIVRE);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("EPG_HORARIO_INVALIDO");
        }

        @Test
        @DisplayName("detecta choque de horario no mesmo canal")
        void detectaChoque() {
            var jornal = programa("Jornal", 0, 2);
            var filme = programa("Filme", 1, 3);
            var novela = programa("Novela", 2, 4);

            assertThat(jornal.conflitaCom(filme)).isTrue();
            assertThat(jornal.conflitaCom(novela)).isFalse();
        }

        @Test
        @DisplayName("acha o que esta no ar e o que vem depois")
        void agoraEaSeguir() {
            var grade = List.of(programa("Jornal", -1, 1), programa("Filme", 1, 3),
                    programa("Madrugada", 3, 6));

            assertThat(ProgramaEpg.agora(grade, AGORA).orElseThrow().titulo()).isEqualTo("Jornal");
            assertThat(ProgramaEpg.aSeguir(grade, AGORA).orElseThrow().titulo()).isEqualTo("Filme");
        }

        @Test
        @DisplayName("grade vazia nao quebra a consulta")
        void gradeVazia() {
            assertThat(ProgramaEpg.agora(List.of(), AGORA)).isEmpty();
            assertThat(ProgramaEpg.aSeguir(List.of(), AGORA)).isEmpty();
        }
    }
}
