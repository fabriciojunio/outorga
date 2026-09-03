package br.com.outorga.domain;

import br.com.outorga.domain.billing.Cupom;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.Periodicidade;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.identity.Dispositivo;
import br.com.outorga.domain.identity.Email;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.identity.Usuario;
import br.com.outorga.domain.playback.Autorizacao;
import br.com.outorga.domain.playback.SessaoDeReproducao;
import br.com.outorga.domain.rights.JanelaDeLicenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.domain.tenant.Marca;
import br.com.outorga.domain.tenant.StatusDoTenant;
import br.com.outorga.domain.tenant.Tenant;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os blocos menores do dominio. Estao juntos porque cada um cabe em poucos
 * casos e espalhar isso em quinze arquivos só aumentaria o custo de ler.
 */
@DisplayName("Blocos do dominio")
class DominioBasicoTest {

    private static final Instant AGORA = Instant.parse("2026-08-24T12:00:00Z");
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USUARIO = UUID.randomUUID();

    @Nested
    @DisplayName("Dinheiro")
    class DoDinheiro {

        @Test
        @DisplayName("formata em real brasileiro")
        void formata() {
            assertThat(Dinheiro.reais(2490).formatado()).isEqualTo("R$ 24,90");
            assertThat(Dinheiro.reais(199000).formatado()).isEqualTo("R$ 1.990,00");
        }

        @Test
        @DisplayName("le valor decimal sem perder centavo")
        void leDecimal() {
            assertThat(Dinheiro.deReais("14.90").centavos()).isEqualTo(1490);
            assertThat(Dinheiro.deReais("0.01").centavos()).isEqualTo(1);
        }

        @Test
        @DisplayName("aplica desconto arredondando ao centavo")
        void aplicaDesconto() {
            assertThat(Dinheiro.reais(2490).comDescontoDe(10).centavos()).isEqualTo(2241);
            assertThat(Dinheiro.reais(2490).comDescontoDe(100)).isEqualTo(Dinheiro.ZERO);
            assertThat(Dinheiro.reais(2490).comDescontoDe(0).centavos()).isEqualTo(2490);
        }

        @Test
        @DisplayName("recusa valor negativo e moeda inválida")
        void recusaInvalidos() {
            assertThatThrownBy(() -> Dinheiro.reais(-1))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Dinheiro(100, "REAL"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("recusa somar moedas diferentes")
        void recusaMoedasDiferentes() {
            assertThatThrownBy(() -> Dinheiro.reais(100).mais(new Dinheiro(100, "USD")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("moedas diferentes");
        }

        @Test
        @DisplayName("subtracao não passa de zero")
        void subtracaoNaoFicaNegativa() {
            assertThat(Dinheiro.reais(100).menos(Dinheiro.reais(500))).isEqualTo(Dinheiro.ZERO);
        }
    }

    @Nested
    @DisplayName("Classificação indicativa")
    class DaClassificacao {

        @Test
        @DisplayName("libera o que cabe no teto do perfil")
        void liberaDentroDoTeto() {
            assertThat(ClassificacaoIndicativa.DOZE_ANOS
                    .liberadaPara(ClassificacaoIndicativa.QUATORZE_ANOS)).isTrue();
            assertThat(ClassificacaoIndicativa.DEZOITO_ANOS
                    .liberadaPara(ClassificacaoIndicativa.QUATORZE_ANOS)).isFalse();
        }

        @Test
        @DisplayName("livre usa o rotulo L")
        void rotuloLivre() {
            assertThat(ClassificacaoIndicativa.LIVRE.rotulo()).isEqualTo("L");
            assertThat(ClassificacaoIndicativa.DEZESSEIS_ANOS.rotulo()).isEqualTo("16");
        }
    }

    @Nested
    @DisplayName("Email")
    class DoEmail {

        @ParameterizedTest
        @ValueSource(strings = {"sem-arroba", "a@b", "@dominio.com", "a a@dominio.com"})
        @DisplayName("recusa formato inválido")
        void recusaInvalido(String entrada) {
            assertThat(Email.de(entrada).falhou()).isTrue();
        }

        @Test
        @DisplayName("normaliza para minusculo e sem espaço")
        void normaliza() {
            assertThat(new Email("  Fabricio@Exemplo.COM ").valor())
                    .isEqualTo("fabricio@exemplo.com");
        }

        @Test
        @DisplayName("mascara o endereço para log e suporte")
        void mascara() {
            assertThat(new Email("fabricio@exemplo.com").mascarado())
                    .isEqualTo("fa***@exemplo.com");
            assertThat(new Email("ab@exemplo.com").mascarado()).isEqualTo("a***@exemplo.com");
        }
    }

    @Nested
    @DisplayName("Usuário")
    class DoUsuario {

        private Usuario novo() {
            return Usuario.criar(TENANT, new Email("a@exemplo.com"), "hash", "Fabricio",
                    Set.of(Papel.ASSINANTE), AGORA).valorOuFalha();
        }

        @Test
        @DisplayName("login certo zera o contador de tentativas")
        void loginCertoZera() {
            var usuario = novo();
            usuario.registrarTentativaDeLogin(false, AGORA);

            var resultado = usuario.registrarTentativaDeLogin(true, AGORA);

            assertThat(resultado.sucesso()).isTrue();
            assertThat(usuario.tentativasSeguidas()).isZero();
            assertThat(usuario.ultimoAcesso()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("bloqueia a conta na quinta tentativa errada")
        void bloqueiaNaQuinta() {
            var usuario = novo();

            for (int i = 0; i < Usuario.TENTATIVAS_ATE_BLOQUEAR; i++) {
                usuario.registrarTentativaDeLogin(false, AGORA);
            }

            assertThat(usuario.estaBloqueado(AGORA)).isTrue();
            assertThat(usuario.registrarTentativaDeLogin(true, AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("CONTA_BLOQUEADA");
        }

        @Test
        @DisplayName("bloqueio expira depois do prazo")
        void bloqueioExpira() {
            var usuario = novo();
            for (int i = 0; i < Usuario.TENTATIVAS_ATE_BLOQUEAR; i++) {
                usuario.registrarTentativaDeLogin(false, AGORA);
            }

            var depois = AGORA.plus(Usuario.DURACAO_DO_BLOQUEIO).plusSeconds(1);

            assertThat(usuario.estaBloqueado(depois)).isFalse();
            assertThat(usuario.registrarTentativaDeLogin(true, depois).sucesso()).isTrue();
        }

        @Test
        @DisplayName("conta desativada recusa login mesmo com senha certa")
        void desativadaRecusa() {
            var usuario = novo();
            usuario.desativar();

            assertThat(usuario.registrarTentativaDeLogin(true, AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("CONTA_INATIVA");
        }

        @Test
        @DisplayName("anonimizar troca o que identifica e trava a conta")
        void anonimiza() {
            var usuario = novo();

            usuario.anonimizar(AGORA);

            assertThat(usuario.anonimizado()).isTrue();
            assertThat(usuario.ativo()).isFalse();
            assertThat(usuario.nome()).isEqualTo("Titular removido");
            assertThat(usuario.email().valor()).doesNotContain("a@exemplo.com");
            assertThat(usuario.email().valor()).contains(usuario.id().toString());
        }

        @Test
        @DisplayName("papeis decidem o que a conta pode fazer")
        void papeisDecidem() {
            var editor = Usuario.criar(TENANT, new Email("e@exemplo.com"), "h", "Editor",
                    Set.of(Papel.EDITOR), AGORA).valorOuFalha();

            assertThat(editor.acessaPainel()).isTrue();
            assertThat(editor.podePublicarCatalogo()).isTrue();
            assertThat(editor.podeMexerEmCobranca()).isFalse();
            assertThat(novo().acessaPainel()).isFalse();
        }

        @Test
        @DisplayName("recusa criacao sem papel")
        void recusaSemPapel() {
            var resultado = Usuario.criar(TENANT, new Email("a@exemplo.com"), "h", "Nome",
                    Set.of(), AGORA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("USUARIO_SEM_PAPEL");
        }
    }

    @Nested
    @DisplayName("Perfil")
    class DoPerfil {

        @Test
        @DisplayName("perfil infantil nasce com teto de 10 anos")
        void infantilTemTetoBaixo() {
            var perfil = Perfil.criar(USUARIO, "Kids", ClassificacaoIndicativa.DEZOITO_ANOS, true, 0)
                    .valorOuFalha();

            assertThat(perfil.tetoDeClassificacao()).isEqualTo(ClassificacaoIndicativa.DEZ_ANOS);
        }

        @Test
        @DisplayName("perfil infantil não aceita teto acima de 12")
        void infantilNaoSobe() {
            var perfil = Perfil.criar(USUARIO, "Kids", null, true, 0).valorOuFalha();

            assertThat(perfil.ajustarTeto(ClassificacaoIndicativa.DEZESSEIS_ANOS)
                    .falha().orElseThrow().codigo()).isEqualTo("TETO_INFANTIL");
        }

        @Test
        @DisplayName("respeita o limite de perfis por conta")
        void respeitaLimite() {
            var resultado = Perfil.criar(USUARIO, "Quinto", null, false, Perfil.MAXIMO_POR_CONTA);

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("LIMITE_DE_PERFIS");
        }

        @Test
        @DisplayName("pin marca o perfil como protegido")
        void pinProtege() {
            var perfil = Perfil.criar(USUARIO, "Adulto", null, false, 0).valorOuFalha();
            assertThat(perfil.protegidoPorPin()).isFalse();

            perfil.definirPin("hash-do-pin");

            assertThat(perfil.protegidoPorPin()).isTrue();
        }
    }

    @Nested
    @DisplayName("Plano")
    class DoPlano {

        @Test
        @DisplayName("limita a qualidade pedida ao teto do plano")
        void limitaQualidade() {
            var plano = Plano.criar(TENANT, "Start", Dinheiro.reais(1490), Periodicidade.MENSAL, 1,
                    Qualidade.HD).valorOuFalha();

            assertThat(plano.limitar(Qualidade.ULTRA_HD)).isEqualTo(Qualidade.HD);
            assertThat(plano.limitar(Qualidade.SD)).isEqualTo(Qualidade.SD);
        }

        @Test
        @DisplayName("aparelhos registraveis são o dobro das telas")
        void dobroDeAparelhos() {
            var plano = Plano.criar(TENANT, "Familia", Dinheiro.reais(2490), Periodicidade.MENSAL, 2,
                    Qualidade.FULL_HD).valorOuFalha();

            assertThat(plano.dispositivosRegistraveis()).isEqualTo(4);
        }

        @Test
        @DisplayName("recusa telas fora da faixa aceita")
        void recusaTelasInvalidas() {
            assertThat(Plano.criar(TENANT, "X", Dinheiro.reais(100), Periodicidade.MENSAL, 0,
                    Qualidade.HD).falha().orElseThrow().codigo()).isEqualTo("PLANO_TELAS_INVALIDO");
            assertThat(Plano.criar(TENANT, "X", Dinheiro.reais(100), Periodicidade.MENSAL, 99,
                    Qualidade.HD).falha().orElseThrow().codigo()).isEqualTo("PLANO_TELAS_INVALIDO");
        }

        @Test
        @DisplayName("preço com cupom aplica o desconto")
        void precoComCupom() {
            var plano = Plano.criar(TENANT, "Familia", Dinheiro.reais(2490), Periodicidade.MENSAL, 2,
                    Qualidade.FULL_HD).valorOuFalha();
            var cupom = Cupom.criar(TENANT, "primeiro", 50, null, 10).valorOuFalha();

            assertThat(plano.precoCom(cupom).centavos()).isEqualTo(1245);
            assertThat(plano.precoCom(null).centavos()).isEqualTo(2490);
        }
    }

    @Nested
    @DisplayName("Cupom")
    class DoCupom {

        @Test
        @DisplayName("normaliza o código para maiusculo")
        void normalizaCodigo() {
            assertThat(Cupom.criar(TENANT, " lancamento ", 20, null, 5).valorOuFalha().codigo())
                    .isEqualTo("LANCAMENTO");
        }

        @Test
        @DisplayName("esgota depois do número máximo de usos")
        void esgota() {
            var cupom = Cupom.criar(TENANT, "X", 20, null, 2).valorOuFalha();

            assertThat(cupom.resgatar(AGORA).sucesso()).isTrue();
            assertThat(cupom.resgatar(AGORA).sucesso()).isTrue();
            assertThat(cupom.resgatar(AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("CUPOM_ESGOTADO");
        }

        @Test
        @DisplayName("recusa depois do prazo")
        void recusaVencido() {
            var cupom = Cupom.criar(TENANT, "X", 20, AGORA, 5).valorOuFalha();

            assertThat(cupom.resgatar(AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("CUPOM_VENCIDO");
        }

        @Test
        @DisplayName("recusa percentual fora da faixa")
        void recusaPercentualInvalido() {
            assertThat(Cupom.criar(TENANT, "X", 0, null, 5).falhou()).isTrue();
            assertThat(Cupom.criar(TENANT, "X", 101, null, 5).falhou()).isTrue();
        }
    }

    @Nested
    @DisplayName("Tenant")
    class DoTenant {

        @Test
        @DisplayName("recusa identificador fora do padrão")
        void recusaSlugInvalido() {
            assertThat(Tenant.abrir("A B", "Nome", null, null, AGORA).falha().orElseThrow().codigo())
                    .isEqualTo("TENANT_SLUG_INVALIDO");
            assertThat(Tenant.abrir("ab", "Nome", null, null, AGORA).falhou()).isTrue();
        }

        @Test
        @DisplayName("em implantacao só aceita trafego dentro do período de teste")
        void implantacaoUsaPeriodoDeTeste() {
            var tenant = Tenant.abrir("cineserra", "Cine Serra", null, null, AGORA).valorOuFalha();

            assertThat(tenant.status()).isEqualTo(StatusDoTenant.EM_IMPLANTACAO);
            assertThat(tenant.aceitaTrafegoDeEspectador(AGORA)).isFalse();

            tenant.definirPeriodoDeTeste(AGORA.plus(Duration.ofDays(14)));

            assertThat(tenant.aceitaTrafegoDeEspectador(AGORA)).isTrue();
            assertThat(tenant.aceitaTrafegoDeEspectador(AGORA.plus(Duration.ofDays(15)))).isFalse();
        }

        @Test
        @DisplayName("suspenso guarda o motivo e barra espectador")
        void suspensoBarra() {
            var tenant = Tenant.abrir("cineserra", "Cine Serra", null, null, AGORA).valorOuFalha();
            tenant.liberarParaProducao();

            tenant.suspender("inadimplencia");

            assertThat(tenant.aceitaTrafegoDeEspectador(AGORA)).isFalse();
            assertThat(tenant.motivoDaSuspensao()).isEqualTo("inadimplencia");
        }

        @Test
        @DisplayName("encerrado não volta a operar")
        void encerradoNaoVolta() {
            var tenant = Tenant.abrir("cineserra", "Cine Serra", null, null, AGORA).valorOuFalha();
            tenant.encerrar();

            assertThat(tenant.liberarParaProducao().falha().orElseThrow().codigo())
                    .isEqualTo("TENANT_ENCERRADO");
        }

        @Test
        @DisplayName("marca recusa cor fora do hexadecimal")
        void marcaValidaCor() {
            assertThatThrownBy(() -> new Marca("Nome", null, "azul", null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new Marca("Nome", null, null, null).corPrimaria()).isEqualTo("#e6b800");
        }
    }

    @Nested
    @DisplayName("Janela de licença")
    class DaJanela {

        @Test
        @DisplayName("recusa fim antes do início")
        void recusaFimAntesDoInicio() {
            assertThatThrownBy(() -> new JanelaDeLicenca(AGORA, AGORA.minusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("o instante do fim já está fora da janela")
        void fimEExclusivo() {
            var janela = new JanelaDeLicenca(AGORA, AGORA.plus(Duration.ofDays(1)));

            assertThat(janela.contem(AGORA)).isTrue();
            assertThat(janela.contem(AGORA.plus(Duration.ofDays(1)))).isFalse();
            assertThat(janela.expiradaEm(AGORA.plus(Duration.ofDays(1)))).isTrue();
        }
    }

    @Nested
    @DisplayName("Território")
    class DoTerritorio {

        @Test
        @DisplayName("aceita código ISO e recusa invento")
        void validaCodigo() {
            assertThat(Territorio.de("br").valorOuFalha()).isEqualTo(Territorio.BRASIL);
            assertThat(Territorio.de("XX").falha().orElseThrow().codigo())
                    .isEqualTo("TERRITORIO_INVALIDO");
        }

        @Test
        @DisplayName("mundial cobre todos, pais cobre só a si mesmo")
        void cobertura() {
            assertThat(Territorio.MUNDIAL.cobre(new Territorio("JP"))).isTrue();
            assertThat(Territorio.BRASIL.cobre(new Territorio("JP"))).isFalse();
            assertThat(Territorio.BRASIL.cobre(Territorio.BRASIL)).isTrue();
        }
    }

    @Nested
    @DisplayName("Sessão de reprodução")
    class DaSessao {

        private SessaoDeReproducao aberta() {
            var autorizacao = new Autorizacao(UUID.randomUUID(), TENANT, null, UUID.randomUUID(),
                    "acervo/x", Qualidade.HD, AGORA.plusSeconds(300), UUID.randomUUID());
            return SessaoDeReproducao.abrir(autorizacao, USUARIO, "aparelho-1", AGORA);
        }

        @Test
        @DisplayName("sessão sem sinal por tempo demais deixa de contar como viva")
        void morreSemSinal() {
            var sessao = aberta();

            assertThat(sessao.viva(AGORA.plus(Duration.ofMinutes(1)))).isTrue();
            assertThat(sessao.viva(AGORA.plus(Duration.ofMinutes(3)))).isFalse();
        }

        @Test
        @DisplayName("sinal de vida renova e guarda a posicao")
        void sinalRenova() {
            var sessao = aberta();

            sessao.sinalDeVida(1200, AGORA.plus(Duration.ofMinutes(1)));

            assertThat(sessao.viva(AGORA.plus(Duration.ofMinutes(2)))).isTrue();
            assertThat(sessao.posicaoEmSegundos()).isEqualTo(1200);
        }

        @Test
        @DisplayName("posicao negativa e ignorada")
        void ignoraPosicaoNegativa() {
            var sessao = aberta();
            sessao.sinalDeVida(500, AGORA);

            sessao.sinalDeVida(-1, AGORA.plusSeconds(30));

            assertThat(sessao.posicaoEmSegundos()).isEqualTo(500);
        }

        @Test
        @DisplayName("sessão fechada não está viva nem com sinal recente")
        void fechadaNaoVive() {
            var sessao = aberta();
            sessao.fechar(AGORA);

            assertThat(sessao.viva(AGORA)).isFalse();
        }
    }

    @Nested
    @DisplayName("Dispositivo")
    class DoDispositivo {

        @Test
        @DisplayName("usa o tipo como apelido quando não vem nome")
        void apelidoPadrao() {
            var dispositivo = Dispositivo.registrar(USUARIO, "id-1", TipoDeDispositivo.ANDROID,
                    "  ", AGORA).valorOuFalha();

            assertThat(dispositivo.apelido()).isEqualTo("ANDROID");
        }

        @Test
        @DisplayName("recusa registro sem identificador")
        void recusaSemIdentificador() {
            assertThat(Dispositivo.registrar(USUARIO, "  ", TipoDeDispositivo.WEB, "x", AGORA)
                    .falha().orElseThrow().codigo()).isEqualTo("DISPOSITIVO_SEM_ID");
        }
    }

    @Nested
    @DisplayName("Result")
    class DoResult {

        @Test
        @DisplayName("mapear só acontece no sucesso")
        void mapearSoNoSucesso() {
            assertThat(Result.ok(2).mapear(n -> n * 3).valorOuFalha()).isEqualTo(6);

            Result<Integer> erro = Result.erro("X", "deu ruim");
            assertThat(erro.mapear(n -> n * 3).falha().orElseThrow().codigo()).isEqualTo("X");
        }

        @Test
        @DisplayName("encadear para no primeiro erro")
        void encadeiaAteOErro() {
            var resultado = Result.ok(2)
                    .entao(n -> Result.ok(n + 1))
                    .entao(n -> Result.<Integer>erro("PAROU", "aqui"))
                    .entao(n -> Result.ok(n * 100));

            assertThat(resultado.falha().orElseThrow().codigo()).isEqualTo("PAROU");
        }

        @Test
        @DisplayName("acessar valor de um erro estoura em vez de devolver nulo")
        void valorDeErroEstoura() {
            Result<String> erro = Result.erro("X", "deu ruim");

            assertThatThrownBy(erro::valorOuFalha)
                    .isInstanceOf(java.util.NoSuchElementException.class)
                    .hasMessageContaining("X");
        }

        @Test
        @DisplayName("detalhes da falha são acumulaveis e imutaveis")
        void detalhesAcumulam() {
            var falha = new FalhaDeNegocio("X", "msg").com("a", 1).com("b", "dois");

            assertThat(falha.detalhes()).containsEntry("a", 1).containsEntry("b", "dois");
            assertThatThrownBy(() -> falha.detalhes().put("c", 3))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("ouEntao devolve a alternativa no erro")
        void ouEntao() {
            assertThat(Result.<String>erro("X", "y").ouEntao("padrao")).isEqualTo("padrao");
            assertThat(Result.ok("valor").ouEntao("padrao")).isEqualTo("valor");
        }
    }
}
