package br.com.mirante.api;

import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.application.usecases.billing.GerirPlanos;
import br.com.mirante.application.usecases.catalog.ListarCatalogo;
import br.com.mirante.application.usecases.live.GerirCanais;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Vitrine: o que da para ver antes de entrar. Catalogo, planos, canais e a
 * identidade visual do cliente, tudo pelo slug do servico.
 *
 * O catalogo aqui ja sai filtrado por publicacao, e publicacao so existe com
 * licenca vigente. Ou seja: nao ha caminho publico que liste uma obra sem
 * direito, nem mesmo a capa.
 */
@RestController
@RequestMapping("/api/v1/publico/{servico}")
public class CatalogoPublicoController {

    private final Repositorios.DeTenant tenants;
    private final Repositorios.DeTitulo titulos;
    private final ListarCatalogo catalogo;
    private final GerirPlanos planos;
    private final GerirCanais canais;
    private final Clock relogio;

    public CatalogoPublicoController(Repositorios.DeTenant tenants, Repositorios.DeTitulo titulos,
                                     ListarCatalogo catalogo, GerirPlanos planos, GerirCanais canais,
                                     Clock relogio) {
        this.tenants = tenants;
        this.titulos = titulos;
        this.catalogo = catalogo;
        this.planos = planos;
        this.canais = canais;
        this.relogio = relogio;
    }

    @GetMapping("/identidade")
    public ResponseEntity<?> identidade(@PathVariable String servico) {
        var tenant = tenants.porSlug(servico);
        return tenant
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(
                        Vistas.Identidade.de(t, relogio.instant())))
                .orElseGet(() -> Respostas.de(Result.erro(Falhas.naoEncontrado("Servico"))));
    }

    @GetMapping("/catalogo")
    public ResponseEntity<?> listar(@PathVariable String servico,
                                    @RequestParam(required = false) UUID perfil,
                                    @RequestParam(defaultValue = "0") int pagina,
                                    @RequestParam(defaultValue = "24") int tamanho) {
        var tenant = tenants.porSlug(servico);
        if (tenant.isEmpty()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Servico")));
        }
        return Respostas.de(catalogo.executar(tenant.get().id(), perfil, pagina, tamanho),
                lista -> lista.stream().map(Vistas.TituloResumido::de).toList());
    }

    @GetMapping("/busca")
    public ResponseEntity<?> buscar(@PathVariable String servico,
                                    @RequestParam String q,
                                    @RequestParam(required = false) UUID perfil,
                                    @RequestParam(defaultValue = "24") int limite) {
        var tenant = tenants.porSlug(servico);
        if (tenant.isEmpty()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Servico")));
        }
        return Respostas.de(catalogo.buscar(tenant.get().id(), perfil, q, limite),
                lista -> lista.stream().map(Vistas.TituloResumido::de).toList());
    }

    @GetMapping("/titulos/{id}")
    public ResponseEntity<?> detalhar(@PathVariable String servico, @PathVariable UUID id) {
        var tenant = tenants.porSlug(servico);
        if (tenant.isEmpty()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Servico")));
        }
        var titulo = titulos.porId(tenant.get().id(), id);
        if (titulo.isEmpty() || !titulo.get().noAr()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Titulo")));
        }
        return ResponseEntity.ok(Vistas.TituloDetalhado.de(titulo.get()));
    }

    @GetMapping("/planos")
    public ResponseEntity<?> planos(@PathVariable String servico) {
        var tenant = tenants.porSlug(servico);
        if (tenant.isEmpty()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Servico")));
        }
        List<Vistas.PlanoVisto> vistos = planos.emVenda(tenant.get().id()).stream()
                .map(Vistas.PlanoVisto::de).toList();
        return ResponseEntity.ok(vistos);
    }

    @GetMapping("/canais")
    public ResponseEntity<?> canais(@PathVariable String servico,
                                    @RequestParam(required = false) UUID perfil) {
        var tenant = tenants.porSlug(servico);
        if (tenant.isEmpty()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Servico")));
        }
        return Respostas.de(canais.grade(tenant.get().id(), perfil),
                lista -> lista.stream().map(Vistas.CanalVisto::de).toList());
    }
}
