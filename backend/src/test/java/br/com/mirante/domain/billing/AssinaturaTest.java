package br.com.mirante.domain.billing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Assinatura")
class AssinaturaTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-24T12:00:00Z");

    private static Plano planoMensal(int diasDeTeste) {
        var plano = Plano.criar(TENANT, "Familia", Dinheiro.reais(2490), Periodicidade.MENSAL, 2,
                Qualidade.FULL_HD).valorOuFalha();
        plano.definirDiasDeTeste(diasDeTeste);
        return plano;
    }

    @Nested
    @DisplayName("na abertura")
    class NaAbertura {

        @Test
        @DisplayName("plano com teste comeca liberando acesso")
        void comTesteLiberaAcesso() {
            var plano = planoMensal(7);

            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();

            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.EM_TESTE);
            assertThat(assinatura.permiteAssistir(AGORA)).isTrue();
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(6)))).isTrue();
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(8)))).isFalse();
        }

        @Test
        @DisplayName("plano sem teste nasce sem acesso, esperando o pagamento")
        void semTesteNaoLibera() {
            var assinatura = Assinatura.abrir(TENANT, USUARIO, planoMensal(0), AGORA).valorOuFalha();

            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.INADIMPLENTE);
            assertThat(assinatura.permiteAssistir(AGORA)).isFalse();
        }

        @Test
        @DisplayName("recusa plano fora de venda")
        void recusaPlanoInativo() {
            var plano = planoMensal(0);
            plano.desativar();

            var resultado = Assinatura.abrir(TENANT, USUARIO, plano, AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("PLANO_INATIVO");
        }

        @Test
        @DisplayName("recusa plano de outro cliente")
        void recusaPlanoDeOutroTenant() {
            var deOutro = Plano.criar(UUID.randomUUID(), "Alheio", Dinheiro.reais(1000),
                    Periodicidade.MENSAL, 1, Qualidade.HD).valorOuFalha();

            var resultado = Assinatura.abrir(TENANT, USUARIO, deOutro, AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("PLANO_DE_OUTRO_TENANT");
        }
    }

    @Nested
    @DisplayName("no pagamento")
    class NoPagamento {

        @Test
        @DisplayName("confirmar pagamento ativa e estende o ciclo")
        void confirmarAtiva() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();

            assinatura.confirmarPagamento(plano, AGORA);

            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.ATIVA);
            assertThat(assinatura.fimDoCicloAtual()).isEqualTo(AGORA.plus(Duration.ofDays(30)));
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(29)))).isTrue();
        }

        /**
         * Gateway reentrega webhook. Se cada reentrega somasse um mes a partir
         * de hoje, uma cobranca virava tres meses de graca; somando a partir do
         * fim vigente, o total continua certo.
         */
        @Test
        @DisplayName("confirmacao repetida soma a partir do fim vigente, nao de hoje")
        void confirmacaoRepetidaNaoDaMesDeGraca() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();

            assinatura.confirmarPagamento(plano, AGORA);
            var primeiroCiclo = assinatura.fimDoCicloAtual();
            assinatura.confirmarPagamento(plano, AGORA.plus(Duration.ofSeconds(30)));

            assertThat(assinatura.fimDoCicloAtual())
                    .isEqualTo(primeiroCiclo.plus(Duration.ofDays(30)));
        }

        @Test
        @DisplayName("falha de pagamento abre carencia em vez de cortar na hora")
        void falhaAbreCarencia() {
            var plano = planoMensal(0);
            var mesPassado = AGORA.minus(Duration.ofDays(30));
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, mesPassado).valorOuFalha();
            assinatura.confirmarPagamento(plano, mesPassado);
            assertThat(assinatura.fimDoCicloAtual()).isEqualTo(AGORA);

            assinatura.registrarFalhaDePagamento("cartao recusado", AGORA);

            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.INADIMPLENTE);
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(2)))).isTrue();
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(4)))).isFalse();
        }

        @Test
        @DisplayName("segunda falha nao reinicia a carencia")
        void segundaFalhaNaoEstendeCarencia() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
            assinatura.confirmarPagamento(plano, AGORA.minus(Duration.ofDays(30)));

            assinatura.registrarFalhaDePagamento("recusado", AGORA);
            var carencia = assinatura.fimDaCarencia();
            assinatura.registrarFalhaDePagamento("recusado de novo", AGORA.plus(Duration.ofDays(1)));

            assertThat(assinatura.fimDaCarencia()).isEqualTo(carencia);
        }

        @Test
        @DisplayName("pagamento depois da falha reativa a assinatura")
        void pagamentoReativa() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
            assinatura.registrarFalhaDePagamento("recusado", AGORA);

            assinatura.confirmarPagamento(plano, AGORA.plus(Duration.ofDays(1)));

            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.ATIVA);
            assertThat(assinatura.fimDaCarencia()).isNull();
            assertThat(assinatura.eventos()).extracting(EventoDaAssinatura::tipo)
                    .contains(EventoDaAssinatura.TipoDeEvento.REATIVADA);
        }
    }

    @Nested
    @DisplayName("no cancelamento")
    class NoCancelamento {

        @Test
        @DisplayName("cancelar mantem acesso ate o fim do ciclo pago")
        void cancelarMantemAcesso() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
            assinatura.confirmarPagamento(plano, AGORA);

            assinatura.cancelar("achei caro", AGORA.plus(Duration.ofDays(1)));

            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.CANCELADA);
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(20)))).isTrue();
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(31)))).isFalse();
        }

        @Test
        @DisplayName("nao aceita cancelar duas vezes")
        void naoCancelaDuasVezes() {
            var assinatura = Assinatura.abrir(TENANT, USUARIO, planoMensal(7), AGORA).valorOuFalha();
            assinatura.cancelar("motivo", AGORA);

            var segundo = assinatura.cancelar("de novo", AGORA);

            assertThat(segundo.falha().orElseThrow().codigo()).isEqualTo("CANCELAMENTO_JA_PEDIDO");
        }

        @Test
        @DisplayName("passagem do tempo encerra o que venceu e nao voltou")
        void tempoEncerra() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
            assinatura.confirmarPagamento(plano, AGORA);

            boolean mudou = assinatura.aplicarPassagemDoTempo(AGORA.plus(Duration.ofDays(31)));

            assertThat(mudou).isTrue();
            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.ENCERRADA);
            assertThat(assinatura.permiteAssistir(AGORA.plus(Duration.ofDays(31)))).isFalse();
        }

        @Test
        @DisplayName("passagem do tempo nao mexe em assinatura em dia")
        void tempoNaoMexeEmDia() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
            assinatura.confirmarPagamento(plano, AGORA);

            assertThat(assinatura.aplicarPassagemDoTempo(AGORA.plus(Duration.ofDays(3)))).isFalse();
        }

        @Test
        @DisplayName("assinatura encerrada nao aceita mais pagamento")
        void encerradaNaoAceitaPagamento() {
            var plano = planoMensal(0);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
            assinatura.aplicarPassagemDoTempo(AGORA.plus(Duration.ofDays(10)));

            assertThat(assinatura.confirmarPagamento(plano, AGORA.plus(Duration.ofDays(11)))
                    .falha().orElseThrow().codigo()).isEqualTo("ASSINATURA_ENCERRADA");
        }
    }

    @Nested
    @DisplayName("na troca de plano")
    class NaTrocaDePlano {

        @Test
        @DisplayName("troca para outro plano do mesmo cliente")
        void trocaPlano() {
            var assinatura = Assinatura.abrir(TENANT, USUARIO, planoMensal(7), AGORA).valorOuFalha();
            var novo = Plano.criar(TENANT, "Start", Dinheiro.reais(1490), Periodicidade.MENSAL, 1,
                    Qualidade.HD).valorOuFalha();

            var resultado = assinatura.trocarPlano(novo, AGORA);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(assinatura.planoId()).isEqualTo(novo.id());
        }

        @Test
        @DisplayName("recusa troca para o mesmo plano")
        void recusaMesmoPlano() {
            var plano = planoMensal(7);
            var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();

            assertThat(assinatura.trocarPlano(plano, AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("MESMO_PLANO");
        }
    }

    @Test
    @DisplayName("a linha do tempo registra cada passo")
    void registraLinhaDoTempo() {
        var plano = planoMensal(7);
        var assinatura = Assinatura.abrir(TENANT, USUARIO, plano, AGORA).valorOuFalha();
        assinatura.confirmarPagamento(plano, AGORA.plus(Duration.ofDays(7)));
        assinatura.cancelar("saiu", AGORA.plus(Duration.ofDays(20)));

        assertThat(assinatura.eventos()).extracting(EventoDaAssinatura::tipo).containsExactly(
                EventoDaAssinatura.TipoDeEvento.CRIADA,
                EventoDaAssinatura.TipoDeEvento.TESTE_INICIADO,
                EventoDaAssinatura.TipoDeEvento.PAGAMENTO_CONFIRMADO,
                EventoDaAssinatura.TipoDeEvento.CANCELAMENTO_PEDIDO);
    }
}
