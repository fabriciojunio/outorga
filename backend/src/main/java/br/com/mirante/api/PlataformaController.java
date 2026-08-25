package br.com.mirante.api;

import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.application.usecases.rights.RevisarDireitosVigentes;
import br.com.mirante.application.usecases.tenant.AdministrarTenants;
import br.com.mirante.infrastructure.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

/**
 * Operacao da plataforma. So o time do Mirante entra aqui: e daqui que
 * cliente novo nasce.
 */
@RestController
@RequestMapping("/api/v1/plataforma")
@PreAuthorize("hasRole('ADMIN_PLATAFORMA')")
public class PlataformaController {

    private final AdministrarTenants tenants;
    private final Repositorios.DeTenant repositorio;
    private final RevisarDireitosVigentes revisarDireitos;
    private final Clock relogio;

    public PlataformaController(AdministrarTenants tenants, Repositorios.DeTenant repositorio,
                                RevisarDireitosVigentes revisarDireitos, Clock relogio) {
        this.tenants = tenants;
        this.repositorio = repositorio;
        this.revisarDireitos = revisarDireitos;
        this.relogio = relogio;
    }

    public record NovoCliente(
            @NotBlank @Size(min = 3, max = 32) String slug,
            @NotBlank String nome,
            String documento,
            String dominioProprio,
            String nomeExibido,
            String logoUri,
            String corPrimaria,
            String corDeFundo,
            @NotBlank @Email String emailDoDono,
            @NotBlank String nomeDoDono,
            @NotBlank @Size(min = 10) String senhaDoDono) {
    }

    @PostMapping("/clientes")
    public ResponseEntity<?> abrir(@Valid @RequestBody NovoCliente novo) {
        return Respostas.criado(tenants.abrir(contexto(), new AdministrarTenants.PedidoDeAbertura(
                        novo.slug(), novo.nome(), novo.documento(), novo.dominioProprio(),
                        novo.nomeExibido(), novo.logoUri(), novo.corPrimaria(), novo.corDeFundo(),
                        novo.emailDoDono(), novo.nomeDoDono(), novo.senhaDoDono())),
                aberto -> new ClienteAberto(aberto.tenant().id(), aberto.tenant().slug(),
                        aberto.tenant().nome(), aberto.tenant().status().name(), aberto.donoId()));
    }

    public record ClienteAberto(UUID id, String slug, String nome, String status, UUID donoId) {
    }

    @GetMapping("/clientes")
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(repositorio.todos().stream()
                .map(t -> Vistas.Identidade.de(t, relogio.instant())).toList());
    }

    @PostMapping("/clientes/{id}/producao")
    public ResponseEntity<?> liberar(@PathVariable UUID id) {
        return Respostas.de(tenants.liberarParaProducao(contexto(), id),
                t -> Vistas.Identidade.de(t, relogio.instant()));
    }

    public record Suspensao(String motivo) {
    }

    @PostMapping("/clientes/{id}/suspensao")
    public ResponseEntity<?> suspender(@PathVariable UUID id,
                                       @RequestBody(required = false) Suspensao suspensao) {
        return Respostas.de(tenants.suspender(contexto(), id,
                        suspensao == null ? null : suspensao.motivo()),
                t -> Vistas.Identidade.de(t, relogio.instant()));
    }

    /**
     * Dispara a varredura de direitos sem esperar a hora cheia. Util depois de
     * uma carga grande de catalogo e para provar em demonstracao que o
     * bloqueio por licenca vencida acontece mesmo.
     */
    @PostMapping("/revisao-de-direitos")
    public ResponseEntity<?> revisar() {
        return ResponseEntity.ok(revisarDireitos.executar());
    }

    private static br.com.mirante.application.ContextoDoChamador contexto() {
        return ((UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication()).contexto();
    }
}
