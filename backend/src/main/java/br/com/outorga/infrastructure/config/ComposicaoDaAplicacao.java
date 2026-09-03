package br.com.outorga.infrastructure.config;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.ports.CifradorDeSenha;
import br.com.outorga.application.ports.EmissorDeToken;
import br.com.outorga.application.ports.EntregaDeVideo;
import br.com.outorga.application.ports.GatewayDePagamento;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.application.usecases.billing.AssinarPlano;
import br.com.outorga.application.usecases.billing.CancelarAssinatura;
import br.com.outorga.application.usecases.billing.EncerrarAssinaturasVencidas;
import br.com.outorga.application.usecases.billing.GerirPlanos;
import br.com.outorga.application.usecases.billing.ProcessarEventoDeCobranca;
import br.com.outorga.application.usecases.catalog.CriarTitulo;
import br.com.outorga.application.usecases.catalog.ListarCatalogo;
import br.com.outorga.application.usecases.catalog.PublicarTitulo;
import br.com.outorga.application.usecases.identity.AtenderTitularDeDados;
import br.com.outorga.application.usecases.identity.AutenticarUsuario;
import br.com.outorga.application.usecases.identity.GerirContas;
import br.com.outorga.application.usecases.live.GerirCanais;
import br.com.outorga.application.usecases.playback.AcompanharSessao;
import br.com.outorga.application.usecases.playback.AutorizarReproducao;
import br.com.outorga.application.usecases.rights.CadastrarLicenca;
import br.com.outorga.application.usecases.rights.ComprovarLicenca;
import br.com.outorga.application.usecases.rights.ListarLicencasAVencer;
import br.com.outorga.application.usecases.rights.RescindirLicenca;
import br.com.outorga.application.usecases.rights.RevisarDireitosVigentes;
import br.com.outorga.application.usecases.tenant.AdministrarTenants;
import br.com.outorga.domain.playback.PoliticaDeReproducao;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Raiz de composicao.
 *
 * Os casos de uso são classes comuns, sem anotacao do Spring. O beneficio e
 * concreto: da para instanciar qualquer um deles num teste passando dublês,
 * sem subir contexto, e ler aqui, num arquivo só, de quem cada um depende. Se
 * uma dependencia nova aparecer, ela aparece nesta lista, o que torna o
 * inchaco visivel em vez de escondido atrás de injecao por campo.
 */
@Configuration
public class ComposicaoDaAplicacao {

    @Bean
    public PoliticaDeReproducao politicaDeReproducao() {
        return new PoliticaDeReproducao();
    }

    @Bean
    public Auditor auditor(Repositorios.DeAuditoria auditoria, Clock relogio) {
        return new Auditor(auditoria, relogio);
    }

    @Bean
    public AutenticarUsuario autenticarUsuario(Repositorios.DeUsuario usuarios,
                                               Repositorios.DeTenant tenants,
                                               CifradorDeSenha cifrador, EmissorDeToken emissor,
                                               Auditor auditor, Clock relogio) {
        return new AutenticarUsuario(usuarios, tenants, cifrador, emissor, auditor, relogio);
    }

    @Bean
    public GerirContas gerirContas(Repositorios.DeUsuario usuarios, Repositorios.DePerfil perfis,
                                   Repositorios.DeDispositivo dispositivos, CifradorDeSenha cifrador,
                                   Auditor auditor, Clock relogio) {
        return new GerirContas(usuarios, perfis, dispositivos, cifrador, auditor, relogio);
    }

    @Bean
    public AtenderTitularDeDados atenderTitularDeDados(Repositorios.DeUsuario usuarios,
                                                       Repositorios.DePerfil perfis,
                                                       Repositorios.DeDispositivo dispositivos,
                                                       Repositorios.DeAssinatura assinaturas,
                                                       EmissorDeToken emissor, Auditor auditor,
                                                       Clock relogio) {
        return new AtenderTitularDeDados(usuarios, perfis, dispositivos, assinaturas, emissor,
                auditor, relogio);
    }

    @Bean
    public AdministrarTenants administrarTenants(Repositorios.DeTenant tenants,
                                                 Repositorios.DeUsuario usuarios,
                                                 CifradorDeSenha cifrador, Auditor auditor,
                                                 Clock relogio) {
        return new AdministrarTenants(tenants, usuarios, cifrador, auditor, relogio);
    }

    @Bean
    public CadastrarLicenca cadastrarLicenca(Repositorios.DeLicenca licencas, Auditor auditor) {
        return new CadastrarLicenca(licencas, auditor);
    }

    @Bean
    public ComprovarLicenca comprovarLicenca(Repositorios.DeLicenca licencas, Auditor auditor) {
        return new ComprovarLicenca(licencas, auditor);
    }

    @Bean
    public RescindirLicenca rescindirLicenca(Repositorios.DeLicenca licencas,
                                             Repositorios.DeTitulo titulos,
                                             Repositorios.DeCanal canais, Auditor auditor,
                                             Clock relogio) {
        return new RescindirLicenca(licencas, titulos, canais, auditor, relogio);
    }

    @Bean
    public RevisarDireitosVigentes revisarDireitosVigentes(Repositorios.DeTenant tenants,
                                                           Repositorios.DeTitulo titulos,
                                                           Repositorios.DeCanal canais,
                                                           Repositorios.DeLicenca licencas,
                                                           Auditor auditor, Clock relogio) {
        return new RevisarDireitosVigentes(tenants, titulos, canais, licencas, auditor, relogio);
    }

    @Bean
    public ListarLicencasAVencer listarLicencasAVencer(Repositorios.DeLicenca licencas,
                                                       Repositorios.DeTitulo titulos, Clock relogio) {
        return new ListarLicencasAVencer(licencas, titulos, relogio);
    }

    @Bean
    public CriarTitulo criarTitulo(Repositorios.DeTitulo titulos, Auditor auditor) {
        return new CriarTitulo(titulos, auditor);
    }

    @Bean
    public PublicarTitulo publicarTitulo(Repositorios.DeTitulo titulos,
                                         Repositorios.DeLicenca licencas, Auditor auditor,
                                         Clock relogio) {
        return new PublicarTitulo(titulos, licencas, auditor, relogio);
    }

    @Bean
    public ListarCatalogo listarCatalogo(Repositorios.DeTitulo titulos, Repositorios.DePerfil perfis) {
        return new ListarCatalogo(titulos, perfis);
    }

    @Bean
    public GerirCanais gerirCanais(Repositorios.DeCanal canais, Repositorios.DeLicenca licencas,
                                   Repositorios.DeEpg epg, Repositorios.DePerfil perfis,
                                   Auditor auditor, Clock relogio) {
        return new GerirCanais(canais, licencas, epg, perfis, auditor, relogio);
    }

    @Bean
    public GerirPlanos gerirPlanos(Repositorios.DePlano planos, Auditor auditor) {
        return new GerirPlanos(planos, auditor);
    }

    @Bean
    public AssinarPlano assinarPlano(Repositorios.DeAssinatura assinaturas,
                                     Repositorios.DePlano planos, Repositorios.DeUsuario usuarios,
                                     Repositorios.DeCupom cupons, GatewayDePagamento gateway,
                                     Auditor auditor, Clock relogio) {
        return new AssinarPlano(assinaturas, planos, usuarios, cupons, gateway, auditor, relogio);
    }

    @Bean
    public CancelarAssinatura cancelarAssinatura(Repositorios.DeAssinatura assinaturas,
                                                 GatewayDePagamento gateway, Auditor auditor,
                                                 Clock relogio) {
        return new CancelarAssinatura(assinaturas, gateway, auditor, relogio);
    }

    @Bean
    public ProcessarEventoDeCobranca processarEventoDeCobranca(Repositorios.DeAssinatura assinaturas,
                                                               Repositorios.DePlano planos,
                                                               GatewayDePagamento gateway,
                                                               Auditor auditor, Clock relogio) {
        return new ProcessarEventoDeCobranca(assinaturas, planos, gateway, auditor, relogio);
    }

    @Bean
    public EncerrarAssinaturasVencidas encerrarAssinaturasVencidas(
            Repositorios.DeAssinatura assinaturas, Auditor auditor, Clock relogio) {
        return new EncerrarAssinaturasVencidas(assinaturas, auditor, relogio);
    }

    @Bean
    public AutorizarReproducao autorizarReproducao(Repositorios.DeTenant tenants,
                                                   Repositorios.DeTitulo titulos,
                                                   Repositorios.DeLicenca licencas,
                                                   Repositorios.DeAssinatura assinaturas,
                                                   Repositorios.DePlano planos,
                                                   Repositorios.DePerfil perfis,
                                                   Repositorios.DeDispositivo dispositivos,
                                                   Repositorios.DeSessao sessoes,
                                                   PoliticaDeReproducao politica,
                                                   EntregaDeVideo entrega, Auditor auditor,
                                                   Clock relogio) {
        return new AutorizarReproducao(tenants, titulos, licencas, assinaturas, planos, perfis,
                dispositivos, sessoes, politica, entrega, auditor, relogio);
    }

    @Bean
    public AcompanharSessao acompanharSessao(Repositorios.DeSessao sessoes, Clock relogio) {
        return new AcompanharSessao(sessoes, relogio);
    }
}
