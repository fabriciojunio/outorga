package br.com.outorga.application.usecases;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.CenarioDeTeste;
import br.com.outorga.application.Dubles;
import br.com.outorga.application.usecases.billing.AssinarPlano;
import br.com.outorga.application.usecases.billing.CancelarAssinatura;
import br.com.outorga.application.usecases.billing.ProcessarEventoDeCobranca;
import br.com.outorga.application.usecases.catalog.ListarCatalogo;
import br.com.outorga.application.usecases.catalog.PublicarTitulo;
import br.com.outorga.application.usecases.identity.AtenderTitularDeDados;
import br.com.outorga.application.usecases.identity.AutenticarUsuario;
import br.com.outorga.application.usecases.playback.AutorizarReproducao;
import br.com.outorga.application.usecases.rights.CadastrarLicenca;
import br.com.outorga.application.usecases.rights.RescindirLicenca;
import br.com.outorga.application.usecases.rights.RevisarDireitosVigentes;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.Periodicidade;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.billing.StatusDaAssinatura;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.catalog.StatusDePublicacao;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.playback.PoliticaDeReproducao;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Casos de uso")
class CasosDeUsoTest {

    private CenarioDeTeste cenario;

    @BeforeEach
    void montar() {
        cenario = new CenarioDeTeste();
    }

    @Nested
    @DisplayName("Autenticar usuario")
    class DoLogin {

        private AutenticarUsuario caso() {
            return new AutenticarUsuario(cenario.usuarios, cenario.tenants, cenario.cifrador,
                    cenario.emissor, cenario.auditor, cenario.relogio);
        }

        @Test
        @DisplayName("entra com a senha certa e deixa rastro")
        void entraComSenhaCerta() {
            var saida = caso().executar(new AutenticarUsuario.Entrada(cenario.tenantId(),
                    "assinante@exemplo.com", "senha-do-assinante", "203.0.113.10"));

            assertThat(saida.sucesso()).isTrue();
            assertThat(saida.valorOuFalha().nome()).isEqualTo("Maria");
            assertThat(saida.valorOuFalha().tokens().acesso()).isNotBlank();
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.LOGIN_OK)).isTrue();
        }

        @Test
        @DisplayName("recusa senha errada com a mesma mensagem de e-mail inexistente")
        void mensagemUnicaParaCredencial() {
            var comSenhaErrada = caso().executar(new AutenticarUsuario.Entrada(cenario.tenantId(),
                    "assinante@exemplo.com", "chute", "203.0.113.10"));
            var comEmailInexistente = caso().executar(new AutenticarUsuario.Entrada(
                    cenario.tenantId(), "ninguem@exemplo.com", "chute", "203.0.113.10"));

            assertThat(comSenhaErrada.falha().orElseThrow().codigo())
                    .isEqualTo(comEmailInexistente.falha().orElseThrow().codigo())
                    .isEqualTo("CREDENCIAL_INVALIDA");
            assertThat(cenario.auditorias.quantasVezes(AcaoAuditavel.LOGIN_RECUSADO)).isEqualTo(2);
        }

        @Test
        @DisplayName("bloqueia a conta depois de cinco erros seguidos")
        void bloqueiaDepoisDeCincoErros() {
            for (int i = 0; i < 5; i++) {
                caso().executar(new AutenticarUsuario.Entrada(cenario.tenantId(),
                        "assinante@exemplo.com", "chute", "203.0.113.10"));
            }

            var comSenhaCerta = caso().executar(new AutenticarUsuario.Entrada(cenario.tenantId(),
                    "assinante@exemplo.com", "senha-do-assinante", "203.0.113.10"));

            assertThat(comSenhaCerta.falha().orElseThrow().codigo()).isEqualTo("CONTA_BLOQUEADA");
        }

        @Test
        @DisplayName("recusa quando o servico nao existe")
        void recusaServicoInexistente() {
            var saida = caso().executar(new AutenticarUsuario.Entrada(UUID.randomUUID(),
                    "assinante@exemplo.com", "senha-do-assinante", "203.0.113.10"));

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("SERVICO_NAO_ENCONTRADO");
        }
    }

    @Nested
    @DisplayName("Publicar titulo")
    class DaPublicacao {

        private PublicarTitulo caso() {
            return new PublicarTitulo(cenario.titulos, cenario.licencas, cenario.auditor,
                    cenario.relogio);
        }

        @Test
        @DisplayName("publica e registra qual contrato autorizou")
        void publicaRegistrandoContrato() {
            var novo = Titulo.criarFilme(cenario.tenantId(), "Beira do Rio",
                    ClassificacaoIndicativa.LIVRE, Duration.ofMinutes(88)).valorOuFalha();
            novo.definirVideoDoFilme("acervo/beira");
            cenario.titulos.salvar(novo);

            var saida = caso().executar(cenario.comoEditor(), novo.id(), cenario.licenca.id());

            assertThat(saida.sucesso()).isTrue();
            assertThat(cenario.titulos.dados.get(novo.id()).noAr()).isTrue();
            var registro = cenario.auditorias.dados.stream()
                    .filter(r -> r.acao() == AcaoAuditavel.TITULO_PUBLICADO)
                    .findFirst().orElseThrow();
            assertThat(registro.detalhes()).containsEntry("contrato", "CT-2026-001");
        }

        @Test
        @DisplayName("assinante nao publica nada")
        void assinanteNaoPublica() {
            var saida = caso().executar(cenario.comoAssinante(), cenario.filme.id(),
                    cenario.licenca.id());

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("SEM_PERMISSAO");
        }

        @Test
        @DisplayName("recusa licenca inexistente")
        void recusaLicencaInexistente() {
            var saida = caso().executar(cenario.comoEditor(), cenario.filme.id(), UUID.randomUUID());

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("NAO_ENCONTRADO");
        }
    }

    @Nested
    @DisplayName("Cadastrar licenca")
    class DoCadastroDeLicenca {

        private CadastrarLicenca caso() {
            return new CadastrarLicenca(cenario.licencas, cenario.auditor);
        }

        @Test
        @DisplayName("com comprovacao ja vem vigente")
        void comComprovacaoVemVigente() {
            var saida = caso().executar(cenario.comoEditor(), new CadastrarLicenca.Entrada(
                    "Distribuidora Norte", "CT-2026-050", Set.of("BR"),
                    CenarioDeTeste.AGORA, CenarioDeTeste.AGORA.plus(Duration.ofDays(365)),
                    Set.of(TipoDeDispositivo.WEB), "s3://ct-50.pdf"));

            assertThat(saida.valorOuFalha().vigenteEm(CenarioDeTeste.AGORA)).isTrue();
        }

        @Test
        @DisplayName("sem comprovacao fica em rascunho e nao autoriza publicar")
        void semComprovacaoFicaEmRascunho() {
            var saida = caso().executar(cenario.comoEditor(), new CadastrarLicenca.Entrada(
                    "Distribuidora Norte", "CT-2026-051", Set.of("BR"),
                    CenarioDeTeste.AGORA, null, Set.of(TipoDeDispositivo.WEB), null));

            assertThat(saida.valorOuFalha().vigenteEm(CenarioDeTeste.AGORA)).isFalse();
        }

        @Test
        @DisplayName("recusa territorio inventado")
        void recusaTerritorioInvalido() {
            var saida = caso().executar(cenario.comoEditor(), new CadastrarLicenca.Entrada(
                    "Distribuidora", "CT-X", Set.of("ZZ"), CenarioDeTeste.AGORA, null,
                    Set.of(TipoDeDispositivo.WEB), null));

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("TERRITORIO_INVALIDO");
        }
    }

    @Nested
    @DisplayName("Revisar direitos vigentes")
    class DaVarredura {

        private RevisarDireitosVigentes caso(Clock relogio) {
            return new RevisarDireitosVigentes(cenario.tenants, cenario.titulos, cenario.canais,
                    cenario.licencas, new Auditor(cenario.auditorias, relogio), relogio);
        }

        @Test
        @DisplayName("tira do ar o titulo cuja licenca venceu")
        void tiraDoArQuandoVence() {
            var depoisDoVencimento = CenarioDeTeste.AGORA.plus(Duration.ofDays(6));

            var resultado = caso(cenario.relogioEm(depoisDoVencimento)).executar();

            assertThat(resultado.titulosBloqueados()).isEqualTo(1);
            assertThat(cenario.titulos.dados.get(cenario.filme.id()).status())
                    .isEqualTo(StatusDePublicacao.BLOQUEADO_POR_DIREITO);
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.TITULO_BLOQUEADO_POR_DIREITO))
                    .isTrue();
        }

        @Test
        @DisplayName("nao mexe em nada enquanto a licenca esta em dia")
        void naoMexeComLicencaEmDia() {
            var resultado = caso(cenario.relogio).executar();

            assertThat(resultado.houveMudanca()).isFalse();
            assertThat(cenario.filme.noAr()).isTrue();
        }

        @Test
        @DisplayName("devolve ao ar quando a licenca e renovada")
        void devolveAoArQuandoRenova() {
            var depoisDoVencimento = CenarioDeTeste.AGORA.plus(Duration.ofDays(6));
            caso(cenario.relogioEm(depoisDoVencimento)).executar();

            var renovada = br.com.outorga.domain.rights.Licenca.reconstituir(
                    cenario.licenca.id(), cenario.tenantId(), cenario.licenca.titular(),
                    cenario.licenca.referenciaDoContrato(), cenario.licenca.territorios(),
                    new br.com.outorga.domain.rights.JanelaDeLicenca(
                            CenarioDeTeste.AGORA.minus(Duration.ofDays(60)),
                            CenarioDeTeste.AGORA.plus(Duration.ofDays(400))),
                    cenario.licenca.dispositivosAutorizados(), "s3://renovado.pdf",
                    br.com.outorga.domain.rights.StatusDaLicenca.VIGENTE, null);
            cenario.licencas.salvar(renovada);

            var resultado = caso(cenario.relogioEm(depoisDoVencimento)).executar();

            assertThat(resultado.titulosLiberados()).isEqualTo(1);
            assertThat(cenario.titulos.dados.get(cenario.filme.id()).noAr()).isTrue();
        }
    }

    @Nested
    @DisplayName("Rescindir licenca")
    class DaRescisao {

        @Test
        @DisplayName("tira do ar na hora tudo que dependia daquela licenca")
        void tiraDoArNaHora() {
            var caso = new RescindirLicenca(cenario.licencas, cenario.titulos, cenario.canais,
                    cenario.auditor, cenario.relogio);

            var saida = caso.executar(cenario.comoEditor(), cenario.licenca.id(), "distrato");

            assertThat(saida.valorOuFalha().titulosBloqueados()).isEqualTo(1);
            assertThat(cenario.titulos.dados.get(cenario.filme.id()).status())
                    .isEqualTo(StatusDePublicacao.BLOQUEADO_POR_DIREITO);
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.LICENCA_RESCINDIDA)).isTrue();
        }
    }

    @Nested
    @DisplayName("Autorizar reproducao")
    class DaReproducao {

        private AutorizarReproducao caso(Clock relogio) {
            return new AutorizarReproducao(cenario.tenants, cenario.titulos, cenario.licencas,
                    cenario.assinaturas, cenario.planos, cenario.perfis, cenario.dispositivos,
                    cenario.sessoes, new PoliticaDeReproducao(), cenario.entrega,
                    new Auditor(cenario.auditorias, relogio), relogio);
        }

        private AutorizarReproducao.Entrada pedidoPadrao() {
            return new AutorizarReproducao.Entrada(cenario.filme.id(), null, null,
                    cenario.perfil.id(), "aparelho-web-1", TipoDeDispositivo.WEB, "Notebook",
                    "BR", Qualidade.FULL_HD);
        }

        @Test
        @DisplayName("autoriza, registra o aparelho e abre a sessao")
        void autorizaEabreSessao() {
            var saida = caso(cenario.relogio).executar(cenario.comoAssinante(), pedidoPadrao());

            assertThat(saida.sucesso()).isTrue();
            assertThat(saida.valorOuFalha().manifesto()).contains("acervo/estrada-de-terra");
            assertThat(cenario.sessoes.dados).hasSize(1);
            assertThat(cenario.dispositivos.doUsuario(cenario.assinante.id())).hasSize(1);
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.REPRODUCAO_AUTORIZADA)).isTrue();
        }

        @Test
        @DisplayName("recusa quando a licenca ja venceu, e o motivo fica auditado")
        void recusaLicencaVencida() {
            var depois = CenarioDeTeste.AGORA.plus(Duration.ofDays(6));

            var saida = caso(cenario.relogioEm(depois))
                    .executar(cenario.comoAssinante(), pedidoPadrao());

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("LICENCA_NAO_VIGENTE");
            var registro = cenario.auditorias.dados.stream()
                    .filter(r -> r.acao() == AcaoAuditavel.REPRODUCAO_RECUSADA)
                    .findFirst().orElseThrow();
            assertThat(registro.detalhes()).containsEntry("codigo", "LICENCA_NAO_VIGENTE");
        }

        @Test
        @DisplayName("segura o limite de telas do plano")
        void seguraLimiteDeTelas() {
            var caso = caso(cenario.relogio);
            caso.executar(cenario.comoAssinante(), pedidoPadrao());
            caso.executar(cenario.comoAssinante(), new AutorizarReproducao.Entrada(
                    cenario.filme.id(), null, null, cenario.perfil.id(), "aparelho-web-2",
                    TipoDeDispositivo.WEB, "Celular", "BR", Qualidade.HD));

            var terceira = caso.executar(cenario.comoAssinante(), new AutorizarReproducao.Entrada(
                    cenario.filme.id(), null, null, cenario.perfil.id(), "aparelho-web-3",
                    TipoDeDispositivo.WEB, "Tablet", "BR", Qualidade.HD));

            assertThat(terceira.falha().orElseThrow().codigo()).isEqualTo("LIMITE_DE_TELAS");
        }

        @Test
        @DisplayName("segura o limite de aparelhos registrados na conta")
        void seguraLimiteDeAparelhos() {
            var caso = caso(cenario.relogio);
            for (int i = 1; i <= cenario.plano.dispositivosRegistraveis(); i++) {
                caso.executar(cenario.comoAssinante(), new AutorizarReproducao.Entrada(
                        cenario.filme.id(), null, null, cenario.perfil.id(), "aparelho-" + i,
                        TipoDeDispositivo.WEB, "Aparelho " + i, "BR", Qualidade.HD));
            }

            var extra = caso.executar(cenario.comoAssinante(), new AutorizarReproducao.Entrada(
                    cenario.filme.id(), null, null, cenario.perfil.id(), "aparelho-extra",
                    TipoDeDispositivo.WEB, "Extra", "BR", Qualidade.HD));

            assertThat(extra.falha().orElseThrow().codigo()).isEqualTo("LIMITE_DE_DISPOSITIVOS");
        }

        @Test
        @DisplayName("perfil infantil nao alcanca titulo de 12 anos")
        void perfilInfantilNaoAlcanca() {
            var infantil = Perfil.criar(cenario.assinante.id(), "Kids", null, true, 1)
                    .valorOuFalha();
            cenario.perfis.salvar(infantil);

            var saida = caso(cenario.relogio).executar(cenario.comoAssinante(),
                    new AutorizarReproducao.Entrada(cenario.filme.id(), null, null, infantil.id(),
                            "aparelho-tv", TipoDeDispositivo.WEB, "TV", "BR", Qualidade.HD));

            assertThat(saida.falha().orElseThrow().codigo())
                    .isEqualTo("BLOQUEADO_PELO_CONTROLE_PARENTAL");
        }

        @Test
        @DisplayName("recusa aparelho fora do que o contrato autoriza")
        void recusaAparelhoNaoLicenciado() {
            var saida = caso(cenario.relogio).executar(cenario.comoAssinante(),
                    new AutorizarReproducao.Entrada(cenario.filme.id(), null, null,
                            cenario.perfil.id(), "tv-sala", TipoDeDispositivo.TV_CONECTADA,
                            "TV da sala", "BR", Qualidade.HD));

            assertThat(saida.falha().orElseThrow().codigo())
                    .isEqualTo("DISPOSITIVO_NAO_LICENCIADO");
        }

        @Test
        @DisplayName("recusa quem nao tem assinatura nenhuma")
        void recusaSemAssinatura() {
            cenario.assinaturas.dados.clear();

            var saida = caso(cenario.relogio).executar(cenario.comoAssinante(), pedidoPadrao());

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("SEM_ASSINATURA");
        }

        @Test
        @DisplayName("propaga falha do fornecedor de video sem abrir sessao")
        void propagaFalhaDaEntrega() {
            cenario.entrega.falhar = true;

            var saida = caso(cenario.relogio).executar(cenario.comoAssinante(), pedidoPadrao());

            assertThat(saida.falhou()).isTrue();
            assertThat(cenario.sessoes.dados).isEmpty();
        }
    }

    @Nested
    @DisplayName("Assinar plano")
    class DaAssinatura {

        private AssinarPlano caso() {
            return new AssinarPlano(cenario.assinaturas, cenario.planos, cenario.usuarios,
                    cenario.cupons, cenario.gateway, cenario.auditor, cenario.relogio);
        }

        @Test
        @DisplayName("recusa quem ja tem assinatura ativa")
        void recusaQuemJaAssina() {
            var saida = caso().executar(cenario.comoAssinante(),
                    new AssinarPlano.Entrada(cenario.plano.id(), null, null, null));

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("JA_ASSINA");
        }

        @Test
        @DisplayName("abre a cobranca e devolve o checkout")
        void abreCobranca() {
            cenario.assinaturas.dados.clear();

            var saida = caso().executar(cenario.comoAssinante(),
                    new AssinarPlano.Entrada(cenario.plano.id(), null, "12345678909", null));

            assertThat(saida.sucesso()).isTrue();
            assertThat(saida.valorOuFalha().urlDeCheckout()).startsWith("https://checkout.exemplo/");
            assertThat(saida.valorOuFalha().valorFormatado()).isEqualTo("R$ 24,90");
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.ASSINATURA_ABERTA)).isTrue();
        }

        @Test
        @DisplayName("aplica o cupom no valor cobrado")
        void aplicaCupom() {
            cenario.assinaturas.dados.clear();
            cenario.cupons.salvar(br.com.outorga.domain.billing.Cupom
                    .criar(cenario.tenantId(), "METADE", 50, null, 10).valorOuFalha());

            var saida = caso().executar(cenario.comoAssinante(),
                    new AssinarPlano.Entrada(cenario.plano.id(), "metade", null, null));

            assertThat(saida.valorOuFalha().valorFormatado()).isEqualTo("R$ 12,45");
        }

        @Test
        @DisplayName("recusa cupom que nao existe")
        void recusaCupomInexistente() {
            cenario.assinaturas.dados.clear();

            var saida = caso().executar(cenario.comoAssinante(),
                    new AssinarPlano.Entrada(cenario.plano.id(), "NAOEXISTE", null, null));

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("CUPOM_INEXISTENTE");
        }
    }

    @Nested
    @DisplayName("Processar evento de cobranca")
    class DoWebhook {

        private ProcessarEventoDeCobranca caso() {
            return new ProcessarEventoDeCobranca(cenario.assinaturas, cenario.planos,
                    cenario.gateway, cenario.auditor, cenario.relogio);
        }

        @Test
        @DisplayName("recusa webhook sem assinatura autentica antes de ler o corpo")
        void recusaWebhookFalso() {
            cenario.gateway.autentico = false;
            cenario.gateway.proximoEvento = Dubles.Gateway.confirmado("ref-teste", 2490);

            var saida = caso().executar(java.util.Map.of(), "{}");

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("WEBHOOK_NAO_AUTENTICO");
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.PAGAMENTO_CONFIRMADO)).isFalse();
        }

        @Test
        @DisplayName("pagamento confirmado estende o ciclo e deixa rastro")
        void confirmaPagamento() {
            cenario.gateway.proximoEvento = Dubles.Gateway.confirmado("ref-teste", 2490);
            var cicloAntes = cenario.assinatura.fimDoCicloAtual();

            var saida = caso().executar(java.util.Map.of(), "{}");

            assertThat(saida.sucesso()).isTrue();
            assertThat(cenario.assinaturas.dados.get(cenario.assinatura.id()).fimDoCicloAtual())
                    .isAfter(cicloAntes);
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.PAGAMENTO_CONFIRMADO)).isTrue();
        }

        @Test
        @DisplayName("pagamento recusado abre carencia sem cortar o acesso na hora")
        void recusaAbreCarencia() {
            cenario.gateway.proximoEvento = Dubles.Gateway.recusado("ref-teste", "cartao recusado");

            caso().executar(java.util.Map.of(), "{}");

            var assinatura = cenario.assinaturas.dados.get(cenario.assinatura.id());
            assertThat(assinatura.status()).isEqualTo(StatusDaAssinatura.INADIMPLENTE);
            assertThat(assinatura.permiteAssistir(CenarioDeTeste.AGORA)).isTrue();
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.PAGAMENTO_RECUSADO)).isTrue();
        }

        @Test
        @DisplayName("cobranca de outro ambiente responde ok em vez de estourar")
        void cobrancaDesconhecidaNaoQuebra() {
            cenario.gateway.proximoEvento = Dubles.Gateway.confirmado("ref-de-outro-lugar", 100);

            var saida = caso().executar(java.util.Map.of(), "{}");

            assertThat(saida.sucesso()).isTrue();
            assertThat(saida.valorOuFalha()).contains("nao encontrada");
        }
    }

    @Nested
    @DisplayName("Cancelar assinatura")
    class DoCancelamento {

        @Test
        @DisplayName("cancela aqui e no gateway, mantendo o ciclo pago")
        void cancelaNosDoisLados() {
            var caso = new CancelarAssinatura(cenario.assinaturas, cenario.gateway, cenario.auditor,
                    cenario.relogio);

            var saida = caso.executar(cenario.comoAssinante(), "achei caro");

            assertThat(saida.valorOuFalha().status()).isEqualTo(StatusDaAssinatura.CANCELADA);
            assertThat(cenario.gateway.cancelados).contains("ref-teste");
            assertThat(saida.valorOuFalha().permiteAssistir(CenarioDeTeste.AGORA)).isTrue();
        }
    }

    @Nested
    @DisplayName("Listar catalogo")
    class DoCatalogo {

        @Test
        @DisplayName("esconde do perfil infantil o que passa da classificacao")
        void escondeDoPerfilInfantil() {
            var infantil = Perfil.criar(cenario.assinante.id(), "Kids", null, true, 1)
                    .valorOuFalha();
            cenario.perfis.salvar(infantil);
            var caso = new ListarCatalogo(cenario.titulos, cenario.perfis);

            var comInfantil = caso.executar(cenario.tenantId(), infantil.id(), 0, 20);
            var semPerfil = caso.executar(cenario.tenantId(), null, 0, 20);

            assertThat(comInfantil.valorOuFalha()).isEmpty();
            assertThat(semPerfil.valorOuFalha()).hasSize(1);
        }

        @Test
        @DisplayName("busca exige ao menos dois caracteres")
        void buscaExigeDoisCaracteres() {
            var caso = new ListarCatalogo(cenario.titulos, cenario.perfis);

            assertThat(caso.buscar(cenario.tenantId(), null, "a", 10).falha().orElseThrow().codigo())
                    .isEqualTo("DADO_INVALIDO");
        }

        @Test
        @DisplayName("titulo bloqueado por direito some do catalogo")
        void bloqueadoSomeDoCatalogo() {
            cenario.filme.revisarDireitos(null, CenarioDeTeste.AGORA);
            cenario.titulos.salvar(cenario.filme);
            var caso = new ListarCatalogo(cenario.titulos, cenario.perfis);

            assertThat(caso.executar(cenario.tenantId(), null, 0, 20).valorOuFalha()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Atender titular de dados")
    class DaLgpd {

        private AtenderTitularDeDados caso() {
            return new AtenderTitularDeDados(cenario.usuarios, cenario.perfis, cenario.dispositivos,
                    cenario.assinaturas, cenario.emissor, cenario.auditor, cenario.relogio);
        }

        @Test
        @DisplayName("exporta o que a plataforma guarda sobre a pessoa")
        void exportaDados() {
            var saida = caso().exportar(cenario.comoAssinante());

            assertThat(saida.valorOuFalha().nome()).isEqualTo("Maria");
            assertThat(saida.valorOuFalha().perfis()).contains("Maria");
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.DADO_PESSOAL_EXPORTADO)).isTrue();
        }

        @Test
        @DisplayName("nao apaga conta com assinatura ativa")
        void naoApagaComAssinaturaAtiva() {
            var saida = caso().apagar(cenario.comoAssinante());

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("CONFLITO");
        }

        @Test
        @DisplayName("apaga anonimizando, derrubando sessoes e sumindo com os perfis")
        void apagaAnonimizando() {
            cenario.assinaturas.dados.clear();

            var saida = caso().apagar(cenario.comoAssinante());

            assertThat(saida.sucesso()).isTrue();
            var usuario = cenario.usuarios.dados.get(cenario.assinante.id());
            assertThat(usuario.anonimizado()).isTrue();
            assertThat(usuario.nome()).isEqualTo("Titular removido");
            assertThat(cenario.perfis.doUsuario(cenario.assinante.id())).isEmpty();
            assertThat(cenario.emissor.revogados).contains(cenario.assinante.id());
            assertThat(cenario.auditorias.registrou(AcaoAuditavel.DADO_PESSOAL_APAGADO)).isTrue();
        }

        @Test
        @DisplayName("a auditoria da exclusao guarda o e-mail mascarado, nao o e-mail")
        void auditoriaGuardaMascarado() {
            cenario.assinaturas.dados.clear();

            caso().apagar(cenario.comoAssinante());

            var registro = cenario.auditorias.dados.stream()
                    .filter(r -> r.acao() == AcaoAuditavel.DADO_PESSOAL_APAGADO)
                    .findFirst().orElseThrow();
            assertThat(registro.detalhes().get("email")).isEqualTo("as***@exemplo.com");
        }
    }

    @Nested
    @DisplayName("Isolamento entre clientes")
    class DoIsolamento {

        @Test
        @DisplayName("titulo de um cliente nao aparece na consulta de outro")
        void naoVazaEntreClientes() {
            var outroTenant = br.com.outorga.domain.tenant.Tenant.abrir("outra", "Outra TV", null,
                    null, CenarioDeTeste.AGORA).valorOuFalha();
            cenario.tenants.salvar(outroTenant);

            assertThat(cenario.titulos.porId(outroTenant.id(), cenario.filme.id())).isEmpty();
            assertThat(cenario.titulos.publicados(outroTenant.id(), 0, 50)).isEmpty();
            assertThat(cenario.licencas.porId(outroTenant.id(), cenario.licenca.id())).isEmpty();
        }

        @Test
        @DisplayName("nao publica titulo com licenca de outro cliente")
        void naoPublicaComLicencaAlheia() {
            var outroTenant = br.com.outorga.domain.tenant.Tenant.abrir("outra", "Outra TV", null,
                    null, CenarioDeTeste.AGORA).valorOuFalha();
            cenario.tenants.salvar(outroTenant);
            var licencaAlheia = br.com.outorga.domain.rights.Licenca.cadastrar(outroTenant.id(),
                    "Alheia", "CT-Z", Set.of(br.com.outorga.domain.rights.Territorio.BRASIL),
                    br.com.outorga.domain.rights.JanelaDeLicenca.aPartirDe(
                            CenarioDeTeste.AGORA.minus(Duration.ofDays(1))),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            licencaAlheia.anexarComprovacao("s3://z.pdf");
            cenario.licencas.salvar(licencaAlheia);

            var caso = new PublicarTitulo(cenario.titulos, cenario.licencas, cenario.auditor,
                    cenario.relogio);
            var saida = caso.executar(cenario.comoEditor(), cenario.filme.id(), licencaAlheia.id());

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("NAO_ENCONTRADO");
        }
    }

    @Nested
    @DisplayName("Plano de outro cliente")
    class DoPlanoAlheio {

        @Test
        @DisplayName("nao deixa assinar plano que pertence a outro cliente")
        void naoAssinaPlanoAlheio() {
            cenario.assinaturas.dados.clear();
            var alheio = Plano.criar(UUID.randomUUID(), "Alheio", Dinheiro.reais(100),
                    Periodicidade.MENSAL, 1, Qualidade.HD).valorOuFalha();
            cenario.planos.salvar(alheio);

            var caso = new AssinarPlano(cenario.assinaturas, cenario.planos, cenario.usuarios,
                    cenario.cupons, cenario.gateway, cenario.auditor, cenario.relogio);
            var saida = caso.executar(cenario.comoAssinante(),
                    new AssinarPlano.Entrada(alheio.id(), null, null, null));

            assertThat(saida.falha().orElseThrow().codigo()).isEqualTo("NAO_ENCONTRADO");
        }
    }

    @Nested
    @DisplayName("Assinatura em memoria")
    class DaConsistencia {

        @Test
        @DisplayName("a assinatura salva guarda a referencia do gateway")
        void guardaReferencia() {
            Assinatura salva = cenario.assinaturas.dados.get(cenario.assinatura.id());

            assertThat(salva.referenciaNoGateway()).isEqualTo("ref-teste");
            assertThat(salva.permiteAssistir(Instant.parse("2026-08-25T00:00:00Z"))).isTrue();
        }
    }
}
