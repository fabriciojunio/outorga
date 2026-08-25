package br.com.mirante.api;

import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.application.usecases.billing.GerirPlanos;
import br.com.mirante.application.usecases.catalog.CriarTitulo;
import br.com.mirante.application.usecases.catalog.PublicarTitulo;
import br.com.mirante.application.usecases.live.GerirCanais;
import br.com.mirante.application.usecases.rights.CadastrarLicenca;
import br.com.mirante.application.usecases.rights.ComprovarLicenca;
import br.com.mirante.application.usecases.rights.ListarLicencasAVencer;
import br.com.mirante.application.usecases.rights.RescindirLicenca;
import br.com.mirante.domain.billing.Periodicidade;
import br.com.mirante.domain.billing.Qualidade;
import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.domain.catalog.TipoDeTitulo;
import br.com.mirante.domain.rights.TipoDeDispositivo;
import br.com.mirante.infrastructure.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Painel do cliente: catalogo, licencas, planos e canais.
 *
 * A ordem natural de uso e essa mesma: cadastra a licenca, anexa a
 * comprovacao, cadastra o titulo, publica apontando para a licenca. Tentar
 * pular a licenca da erro, e da de proposito.
 */
@RestController
@RequestMapping("/api/v1/painel")
@PreAuthorize("hasAnyRole('ADMIN_PLATAFORMA','DONO','EDITOR','SUPORTE')")
public class PainelController {

    private final CriarTitulo criarTitulo;
    private final PublicarTitulo publicarTitulo;
    private final CadastrarLicenca cadastrarLicenca;
    private final ComprovarLicenca comprovarLicenca;
    private final RescindirLicenca rescindirLicenca;
    private final ListarLicencasAVencer licencasAVencer;
    private final GerirPlanos planos;
    private final GerirCanais canais;
    private final Repositorios.DeLicenca licencas;
    private final Repositorios.DeTitulo titulos;
    private final Repositorios.DeAuditoria auditoria;

    public PainelController(CriarTitulo criarTitulo, PublicarTitulo publicarTitulo,
                            CadastrarLicenca cadastrarLicenca, ComprovarLicenca comprovarLicenca,
                            RescindirLicenca rescindirLicenca, ListarLicencasAVencer licencasAVencer,
                            GerirPlanos planos, GerirCanais canais, Repositorios.DeLicenca licencas,
                            Repositorios.DeTitulo titulos, Repositorios.DeAuditoria auditoria) {
        this.criarTitulo = criarTitulo;
        this.publicarTitulo = publicarTitulo;
        this.cadastrarLicenca = cadastrarLicenca;
        this.comprovarLicenca = comprovarLicenca;
        this.rescindirLicenca = rescindirLicenca;
        this.licencasAVencer = licencasAVencer;
        this.planos = planos;
        this.canais = canais;
        this.licencas = licencas;
        this.titulos = titulos;
        this.auditoria = auditoria;
    }

    // ---------- Licencas ----------

    public record NovaLicenca(
            @NotBlank String titular,
            @NotBlank String referenciaDoContrato,
            @NotNull Set<String> territorios,
            @NotNull Instant inicio,
            Instant fim,
            @NotNull Set<TipoDeDispositivo> dispositivos,
            String comprovacaoUri) {
    }

    @PostMapping("/licencas")
    public ResponseEntity<?> cadastrarLicenca(@Valid @RequestBody NovaLicenca nova) {
        return Respostas.criado(cadastrarLicenca.executar(contexto(),
                new CadastrarLicenca.Entrada(nova.titular(), nova.referenciaDoContrato(),
                        nova.territorios(), nova.inicio(), nova.fim(), nova.dispositivos(),
                        nova.comprovacaoUri())),
                Vistas.LicencaVista::de);
    }

    @GetMapping("/licencas")
    public ResponseEntity<?> listarLicencas() {
        return ResponseEntity.ok(licencas.doTenant(autenticado().tenantId()).stream()
                .map(Vistas.LicencaVista::de).toList());
    }

    public record Comprovacao(@NotBlank String comprovacaoUri) {
    }

    @PostMapping("/licencas/{id}/comprovacao")
    public ResponseEntity<?> comprovar(@PathVariable UUID id,
                                       @Valid @RequestBody Comprovacao comprovacao) {
        return Respostas.de(comprovarLicenca.executar(contexto(), id, comprovacao.comprovacaoUri()),
                Vistas.LicencaVista::de);
    }

    public record Rescisao(String motivo) {
    }

    @PostMapping("/licencas/{id}/rescisao")
    public ResponseEntity<?> rescindir(@PathVariable UUID id,
                                       @RequestBody(required = false) Rescisao rescisao) {
        return Respostas.de(rescindirLicenca.executar(contexto(), id,
                rescisao == null ? null : rescisao.motivo()));
    }

    @GetMapping("/licencas/a-vencer")
    public ResponseEntity<?> aVencer(@RequestParam(defaultValue = "60") int dias) {
        return Respostas.de(licencasAVencer.executar(contexto(), dias));
    }

    // ---------- Catalogo ----------

    public record NovoTitulo(
            @NotNull TipoDeTitulo tipo,
            @NotBlank String nome,
            String sinopse,
            Integer anoDeProducao,
            @NotNull ClassificacaoIndicativa classificacao,
            Long duracaoSegundos,
            Set<String> generos,
            String capaUri,
            String referenciaDoVideo) {
    }

    @PostMapping("/titulos")
    public ResponseEntity<?> criarTitulo(@Valid @RequestBody NovoTitulo novo) {
        return Respostas.criado(criarTitulo.executar(contexto(), new CriarTitulo.Entrada(
                        novo.tipo(), novo.nome(), novo.sinopse(), novo.anoDeProducao(),
                        novo.classificacao(),
                        novo.duracaoSegundos() == null ? null : Duration.ofSeconds(novo.duracaoSegundos()),
                        novo.generos(), novo.capaUri(), novo.referenciaDoVideo())),
                Vistas.TituloNoPainel::de);
    }

    public record Publicacao(@NotNull UUID licencaId) {
    }

    @PostMapping("/titulos/{id}/publicacao")
    public ResponseEntity<?> publicar(@PathVariable UUID id,
                                      @Valid @RequestBody Publicacao publicacao) {
        return Respostas.de(publicarTitulo.executar(contexto(), id, publicacao.licencaId()),
                Vistas.TituloNoPainel::de);
    }

    @GetMapping("/titulos")
    public ResponseEntity<?> listarTitulos() {
        return ResponseEntity.ok(titulos.sujeitosARevisaoDeDireitos(autenticado().tenantId()).stream()
                .map(Vistas.TituloNoPainel::de).toList());
    }

    // ---------- Planos ----------

    public record NovoPlano(
            @NotBlank String nome,
            String descricao,
            long precoEmCentavos,
            @NotNull Periodicidade periodicidade,
            int telas,
            @NotNull Qualidade qualidade,
            int diasDeTeste) {
    }

    @PostMapping("/planos")
    public ResponseEntity<?> criarPlano(@Valid @RequestBody NovoPlano novo) {
        return Respostas.criado(planos.criar(contexto(), new GerirPlanos.NovoPlano(
                        novo.nome(), novo.descricao(), novo.precoEmCentavos(), novo.periodicidade(),
                        novo.telas(), novo.qualidade(), novo.diasDeTeste())),
                Vistas.PlanoVisto::de);
    }

    // ---------- Canais ----------

    public record NovoCanal(
            @NotBlank String nome,
            int numero,
            @NotNull ClassificacaoIndicativa classificacao,
            String logoUri,
            String urlDaFonte) {
    }

    @PostMapping("/canais")
    public ResponseEntity<?> criarCanal(@Valid @RequestBody NovoCanal novo) {
        return Respostas.criado(canais.cadastrar(contexto(), new GerirCanais.NovoCanal(
                novo.nome(), novo.numero(), novo.classificacao(), novo.logoUri(), novo.urlDaFonte())),
                Vistas.CanalVisto::de);
    }

    @PostMapping("/canais/{id}/no-ar")
    public ResponseEntity<?> colocarNoAr(@PathVariable UUID id,
                                         @Valid @RequestBody Publicacao publicacao) {
        return Respostas.de(canais.colocarNoAr(contexto(), id, publicacao.licencaId()),
                Vistas.CanalVisto::de);
    }

    // ---------- Auditoria ----------

    @GetMapping("/auditoria")
    public ResponseEntity<?> auditoria(@RequestParam(required = false) Instant de,
                                       @RequestParam(required = false) Instant ate,
                                       @RequestParam(defaultValue = "200") int limite) {
        var fim = ate == null ? Instant.now() : ate;
        var inicio = de == null ? fim.minus(Duration.ofDays(7)) : de;
        return ResponseEntity.ok(auditoria.doTenant(autenticado().tenantId(), inicio, fim,
                Math.min(limite, 1000)));
    }

    private static UsuarioAutenticado autenticado() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication();
    }

    private static br.com.mirante.application.ContextoDoChamador contexto() {
        return autenticado().contexto();
    }
}
