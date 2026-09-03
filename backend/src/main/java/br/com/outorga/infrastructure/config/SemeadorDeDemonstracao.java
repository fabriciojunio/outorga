package br.com.outorga.infrastructure.config;

import br.com.outorga.application.ports.CifradorDeSenha;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.billing.Assinatura;
import br.com.outorga.domain.billing.Dinheiro;
import br.com.outorga.domain.billing.Periodicidade;
import br.com.outorga.domain.billing.Plano;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.catalog.ClassificacaoIndicativa;
import br.com.outorga.domain.catalog.Episodio;
import br.com.outorga.domain.catalog.Temporada;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.domain.identity.Email;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.domain.identity.Perfil;
import br.com.outorga.domain.identity.Usuario;
import br.com.outorga.domain.live.CanalAoVivo;
import br.com.outorga.domain.rights.JanelaDeLicenca;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.domain.rights.Territorio;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.domain.tenant.Marca;
import br.com.outorga.domain.tenant.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Carga de demonstração.
 *
 * Roda uma vez, só em modo Demonstração e só quando o banco está vazio. Nunca
 * apaga nada: se já existe cliente cadastrado, sai calado. É o cuidado que
 * evita a história clássica do seed que zerou a base de produção porque
 * alguém esqueceu uma variável de ambiente ligada.
 *
 * O catálogo aqui é de obra fictícia. A plataforma não acompanha acervo, e
 * isso é uma decisão de produto, não uma pendência: quem traz o conteúdo e o
 * direito sobre ele é o cliente.
 */
@Configuration
public class SemeadorDeDemonstracao {

    private static final Logger log = LoggerFactory.getLogger(SemeadorDeDemonstracao.class);

    public static final String SENHA_DE_DEMONSTRACAO = "demonstracao2026";

    @Bean
    public ApplicationRunner semearDemonstracao(ConfiguracaoDaOutorga configuracao,
                                                Repositorios.DeTenant tenants,
                                                Repositorios.DeUsuario usuarios,
                                                Repositorios.DePerfil perfis,
                                                Repositorios.DeLicenca licencas,
                                                Repositorios.DeTitulo titulos,
                                                Repositorios.DePlano planos,
                                                Repositorios.DeAssinatura assinaturas,
                                                Repositorios.DeCanal canais,
                                                CifradorDeSenha cifrador,
                                                Clock relogio) {
        return args -> {
            if (!configuracao.emDemonstracao()) {
                return;
            }
            if (!tenants.todos().isEmpty()) {
                log.info("Base já tem cliente cadastrado. Carga de demonstração não roda.");
                return;
            }

            var agora = relogio.instant();
            log.info("Base vazia e modo demonstração. Criando o cliente de exemplo.");

            var tenant = Tenant.abrir("cineserra", "Cine Serra", "12345678000190",
                    new Marca("Cine Serra", null, "#e6b800", "#0d0f14"), agora).valorOuFalha();
            tenant.liberarParaProducao();
            tenants.salvar(tenant);

            var senha = cifrador.cifrar(SENHA_DE_DEMONSTRACAO);

            var dono = Usuario.criar(tenant.id(), new Email("dono@cineserra.com.br"), senha,
                    "Dono do serviço", Set.of(Papel.DONO), agora).valorOuFalha();
            usuarios.salvar(dono);

            var operador = Usuario.criar(tenant.id(), new Email("plataforma@outorga.app"), senha,
                    "Operação Outorga TV", Set.of(Papel.ADMIN_PLATAFORMA), agora).valorOuFalha();
            usuarios.salvar(operador);

            var espectador = Usuario.criar(tenant.id(), new Email("espectador@exemplo.com"), senha,
                    "Espectador de demonstração", Set.of(Papel.ASSINANTE), agora).valorOuFalha();
            usuarios.salvar(espectador);

            perfis.salvar(Perfil.criar(espectador.id(), "Adulto",
                    ClassificacaoIndicativa.DEZOITO_ANOS, false, 0).valorOuFalha());
            perfis.salvar(Perfil.criar(espectador.id(), "Infantil", null, true, 1).valorOuFalha());

            var start = Plano.criar(tenant.id(), "Start", Dinheiro.reais(1490),
                    Periodicidade.MENSAL, 1, Qualidade.HD).valorOuFalha();
            start.definirDescricao("1 tela, qualidade HD");
            start.definirDiasDeTeste(7);
            planos.salvar(start);

            var familia = Plano.criar(tenant.id(), "Família", Dinheiro.reais(2490),
                    Periodicidade.MENSAL, 2, Qualidade.FULL_HD).valorOuFalha();
            familia.definirDescricao("2 telas, qualidade Full HD");
            familia.definirDiasDeTeste(7);
            planos.salvar(familia);

            var anual = Plano.criar(tenant.id(), "Anual", Dinheiro.reais(19900),
                    Periodicidade.ANUAL, 2, Qualidade.FULL_HD).valorOuFalha();
            anual.definirDescricao("2 telas, pagamento uma vez por ano");
            planos.salvar(anual);

            var assinatura = Assinatura.abrir(tenant.id(), espectador.id(), familia, agora)
                    .valorOuFalha();
            assinatura.confirmarPagamento(familia, agora);
            assinaturas.salvar(assinatura);

            // Uma licença larga para o acervo e uma curta, que vence em dois
            // dias, só para a varredura de direitos ter o que mostrar numa
            // demonstração comercial.
            var licencaLonga = Licenca.cadastrar(tenant.id(), "Produtora Serra Filmes",
                    "CT-2026-001", Set.of(Territorio.BRASIL),
                    new JanelaDeLicenca(agora.minus(Duration.ofDays(30)),
                            agora.plus(Duration.ofDays(720))),
                    Set.of(TipoDeDispositivo.WEB, TipoDeDispositivo.ANDROID,
                            TipoDeDispositivo.TV_CONECTADA)).valorOuFalha();
            licencaLonga.anexarComprovacao("arquivo://contratos/CT-2026-001.pdf");
            licencas.salvar(licencaLonga);

            var licencaCurta = Licenca.cadastrar(tenant.id(), "Distribuidora Cerrado",
                    "CT-2026-014", Set.of(Territorio.BRASIL),
                    new JanelaDeLicenca(agora.minus(Duration.ofDays(300)),
                            agora.plus(Duration.ofDays(2))),
                    Set.of(TipoDeDispositivo.WEB)).valorOuFalha();
            licencaCurta.anexarComprovacao("arquivo://contratos/CT-2026-014.pdf");
            licencas.salvar(licencaCurta);

            publicarFilme(titulos, tenant, licencaLonga, agora, "Estrada de Terra",
                    "Um caminhoneiro atravessa o interior levando uma encomenda que não devia ter aceitado.",
                    2024, ClassificacaoIndicativa.DOZE_ANOS, 96, Set.of("drama", "estrada"));

            publicarFilme(titulos, tenant, licencaLonga, agora, "O Último Trem da Serra",
                    "A história da ferrovia que ligou duas cidades e depois foi esquecida.",
                    2023, ClassificacaoIndicativa.LIVRE, 82, Set.of("documentário"));

            publicarFilme(titulos, tenant, licencaCurta, agora, "Noite de São João",
                    "Uma festa de arraia vira o palco de um acerto de contas antigo.",
                    2025, ClassificacaoIndicativa.QUATORZE_ANOS, 104, Set.of("drama", "suspense"));

            publicarFilme(titulos, tenant, licencaLonga, agora, "Pipoca e Foguete",
                    "Duas crianças montam um foguete no quintal para chegar na lua antes das férias acabarem.",
                    2024, ClassificacaoIndicativa.LIVRE, 78, Set.of("infantil", "aventura"));

            var serie = Titulo.criarSerie(tenant.id(), "Cerrado",
                    ClassificacaoIndicativa.QUATORZE_ANOS).valorOuFalha();
            serie.definirSinopse("Três gerações de uma família disputam a mesma terra.");
            serie.definirAnoDeProducao(2025);
            List.of("drama", "família").forEach(serie::adicionarGenero);
            var temporada = Temporada.criar(1, "Primeira temporada").valorOuFalha();
            temporada.adicionar(Episodio.criar(1, "A cerca", Duration.ofMinutes(46),
                    "acervo/cerrado/s01e01").valorOuFalha());
            temporada.adicionar(Episodio.criar(2, "A cheia", Duration.ofMinutes(44),
                    "acervo/cerrado/s01e02").valorOuFalha());
            temporada.adicionar(Episodio.criar(3, "O inventário", Duration.ofMinutes(48),
                    "acervo/cerrado/s01e03").valorOuFalha());
            serie.adicionarTemporada(temporada);
            serie.publicar(licencaLonga, agora);
            titulos.salvar(serie);

            var canal = CanalAoVivo.cadastrar(tenant.id(), "Serra TV", 10,
                    ClassificacaoIndicativa.LIVRE).valorOuFalha();
            canal.definirFonte("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");
            canal.colocarNoAr(licencaLonga, agora);
            canais.salvar(canal);

            log.info("""
                    Carga de demonstração pronta.
                      serviço: cineserra
                      painel do cliente: dono@cineserra.com.br
                      operação da plataforma: plataforma@outorga.app
                      espectador: espectador@exemplo.com
                      senha de todos: {}
                    """, SENHA_DE_DEMONSTRACAO);
        };
    }

    private void publicarFilme(Repositorios.DeTitulo titulos, Tenant tenant, Licenca licenca,
                               Instant agora, String nome, String sinopse, int ano,
                               ClassificacaoIndicativa classificacao, int minutos,
                               Set<String> generos) {
        var filme = Titulo.criarFilme(tenant.id(), nome, classificacao, Duration.ofMinutes(minutos))
                .valorOuFalha();
        filme.definirSinopse(sinopse);
        filme.definirAnoDeProducao(ano);
        generos.forEach(filme::adicionarGenero);
        filme.definirVideoDoFilme("acervo/" + caminhoDe(nome));
        filme.publicar(licenca, agora);
        titulos.salvar(filme);
    }

    /**
     * Nome do título virado em caminho de arquivo.
     *
     * <p>O acento sai por decomposição Unicode, e não por descarte: sem isso
     * "O Último Trem da Serra" viraria "o--ltimo-trem-da-serra", porque o
     * filtro de letra e dígito não reconhece "u" com acento e o troca por um
     * separador. Caminho de arquivo com buraco no meio do nome é o tipo de
     * coisa que ninguém percebe até procurar o vídeo pelo nome.
     */
    private static String caminhoDe(String nome) {
        String semAcento = java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
