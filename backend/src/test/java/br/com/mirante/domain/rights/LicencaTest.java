package br.com.mirante.domain.rights;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Licenca")
class LicencaTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-24T12:00:00Z");

    private static Licenca vigenteNoBrasil() {
        var licenca = Licenca.cadastrar(TENANT, "Produtora Serra", "CT-2026-001",
                Set.of(Territorio.BRASIL),
                new JanelaDeLicenca(AGORA.minus(Duration.ofDays(30)), AGORA.plus(Duration.ofDays(30))),
                Set.of(TipoDeDispositivo.WEB, TipoDeDispositivo.ANDROID)).valorOuFalha();
        licenca.anexarComprovacao("s3://contratos/ct-2026-001.pdf");
        return licenca;
    }

    @Nested
    @DisplayName("no cadastro")
    class NoCadastro {

        @Test
        @DisplayName("recusa sem titular")
        void recusaSemTitular() {
            var resultado = Licenca.cadastrar(TENANT, "  ", "CT-1", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of(TipoDeDispositivo.WEB));

            assertThat(resultado.falhou()).isTrue();
            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LICENCA_SEM_TITULAR");
        }

        @Test
        @DisplayName("recusa sem referencia de contrato")
        void recusaSemContrato() {
            var resultado = Licenca.cadastrar(TENANT, "Produtora", null, Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of(TipoDeDispositivo.WEB));

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LICENCA_SEM_CONTRATO");
        }

        @Test
        @DisplayName("recusa sem territorio")
        void recusaSemTerritorio() {
            var resultado = Licenca.cadastrar(TENANT, "Produtora", "CT-1", Set.of(),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of(TipoDeDispositivo.WEB));

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LICENCA_SEM_TERRITORIO");
        }

        @Test
        @DisplayName("nasce em rascunho e nao autoriza nada")
        void nasceEmRascunho() {
            var licenca = Licenca.cadastrar(TENANT, "Produtora", "CT-1", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA.minus(Duration.ofDays(1))),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();

            assertThat(licenca.status()).isEqualTo(StatusDaLicenca.RASCUNHO);
            assertThat(licenca.vigenteEm(AGORA)).isFalse();
            assertThat(licenca.autoriza(Territorio.BRASIL, TipoDeDispositivo.WEB, AGORA)).isFalse();
        }
    }

    @Nested
    @DisplayName("na comprovacao")
    class NaComprovacao {

        @Test
        @DisplayName("anexar comprovacao coloca em vigencia")
        void anexarColocaEmVigencia() {
            var licenca = vigenteNoBrasil();

            assertThat(licenca.status()).isEqualTo(StatusDaLicenca.VIGENTE);
            assertThat(licenca.vigenteEm(AGORA)).isTrue();
        }

        @Test
        @DisplayName("recusa comprovacao vazia")
        void recusaComprovacaoVazia() {
            var licenca = Licenca.cadastrar(TENANT, "Produtora", "CT-1", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of(TipoDeDispositivo.WEB)).valorOuFalha();

            var resultado = licenca.anexarComprovacao("   ");

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("COMPROVACAO_VAZIA");
            assertThat(licenca.status()).isEqualTo(StatusDaLicenca.RASCUNHO);
        }

        @Test
        @DisplayName("licenca rescindida nao volta a vigorar")
        void rescindidaNaoVolta() {
            var licenca = vigenteNoBrasil();
            licenca.rescindir("acordo desfeito");

            var resultado = licenca.anexarComprovacao("s3://novo.pdf");

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LICENCA_RESCINDIDA");
            assertThat(licenca.status()).isEqualTo(StatusDaLicenca.RESCINDIDA);
        }
    }

    @Nested
    @DisplayName("ao decidir se autoriza")
    class AoAutorizar {

        @Test
        @DisplayName("autoriza dentro da janela, do territorio e do dispositivo")
        void autorizaQuandoTudoBate() {
            assertThat(vigenteNoBrasil().autoriza(Territorio.BRASIL, TipoDeDispositivo.WEB, AGORA))
                    .isTrue();
        }

        @Test
        @DisplayName("recusa fora do territorio contratado")
        void recusaForaDoTerritorio() {
            assertThat(vigenteNoBrasil()
                    .autoriza(new Territorio("PT"), TipoDeDispositivo.WEB, AGORA)).isFalse();
        }

        @Test
        @DisplayName("recusa dispositivo que o contrato nao cobre")
        void recusaDispositivoForaDoContrato() {
            assertThat(vigenteNoBrasil()
                    .autoriza(Territorio.BRASIL, TipoDeDispositivo.TV_CONECTADA, AGORA)).isFalse();
        }

        @Test
        @DisplayName("recusa depois do fim da janela")
        void recusaDepoisDaJanela() {
            var depois = AGORA.plus(Duration.ofDays(31));

            assertThat(vigenteNoBrasil().autoriza(Territorio.BRASIL, TipoDeDispositivo.WEB, depois))
                    .isFalse();
        }

        @Test
        @DisplayName("recusa antes do inicio da janela")
        void recusaAntesDaJanela() {
            var antes = AGORA.minus(Duration.ofDays(31));

            assertThat(vigenteNoBrasil().autoriza(Territorio.BRASIL, TipoDeDispositivo.WEB, antes))
                    .isFalse();
        }

        @Test
        @DisplayName("licenca mundial cobre qualquer pais")
        void mundialCobreTudo() {
            var licenca = Licenca.cadastrar(TENANT, "Produtora", "CT-2", Set.of(Territorio.MUNDIAL),
                    JanelaDeLicenca.aPartirDe(AGORA.minus(Duration.ofDays(1))),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            licenca.anexarComprovacao("s3://ct-2.pdf");

            assertThat(licenca.cobreTerritorio(new Territorio("JP"))).isTrue();
            assertThat(licenca.cobreTerritorio(Territorio.BRASIL)).isTrue();
        }
    }

    @Nested
    @DisplayName("no aviso de vencimento")
    class NoAvisoDeVencimento {

        @Test
        @DisplayName("acusa licenca que vence dentro do prazo consultado")
        void acusaVencimentoProximo() {
            assertThat(vigenteNoBrasil().venceEmAte(AGORA, 60)).isTrue();
        }

        @Test
        @DisplayName("nao acusa licenca que vence depois do prazo")
        void naoAcusaVencimentoDistante() {
            assertThat(vigenteNoBrasil().venceEmAte(AGORA, 10)).isFalse();
        }

        @Test
        @DisplayName("licenca por prazo indeterminado nunca aparece no aviso")
        void indeterminadaNaoVence() {
            var licenca = Licenca.cadastrar(TENANT, "Produtora", "CT-3", Set.of(Territorio.BRASIL),
                    JanelaDeLicenca.aPartirDe(AGORA), Set.of(TipoDeDispositivo.WEB)).valorOuFalha();

            assertThat(licenca.janela().indeterminada()).isTrue();
            assertThat(licenca.venceEmAte(AGORA, 3650)).isFalse();
        }
    }
}
