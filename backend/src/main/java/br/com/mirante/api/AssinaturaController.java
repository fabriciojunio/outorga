package br.com.mirante.api;

import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.application.usecases.billing.AssinarPlano;
import br.com.mirante.application.usecases.billing.CancelarAssinatura;
import br.com.mirante.infrastructure.security.UsuarioAutenticado;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

/** Assinatura do espectador: contratar, consultar e cancelar. */
@RestController
@RequestMapping("/api/v1/assinaturas")
public class AssinaturaController {

    private final AssinarPlano assinar;
    private final CancelarAssinatura cancelar;
    private final Repositorios.DeAssinatura assinaturas;
    private final Clock relogio;

    public AssinaturaController(AssinarPlano assinar, CancelarAssinatura cancelar,
                                Repositorios.DeAssinatura assinaturas, Clock relogio) {
        this.assinar = assinar;
        this.cancelar = cancelar;
        this.assinaturas = assinaturas;
        this.relogio = relogio;
    }

    public record PedidoDeAssinatura(@NotNull UUID planoId, String cupom, String documento,
                                     String urlDeRetorno) {
    }

    @PostMapping
    public ResponseEntity<?> contratar(@Valid @RequestBody PedidoDeAssinatura pedido) {
        return Respostas.criado(assinar.executar(autenticado().contexto(),
                new AssinarPlano.Entrada(pedido.planoId(), pedido.cupom(), pedido.documento(),
                        pedido.urlDeRetorno())), s -> s);
    }

    @GetMapping("/minha")
    public ResponseEntity<?> minha() {
        var autenticado = autenticado();
        var achada = assinaturas.vigenteDoUsuario(autenticado.tenantId(), autenticado.usuarioId());
        if (achada.isEmpty()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Assinatura")));
        }
        return ResponseEntity.ok(Vistas.AssinaturaVista.de(achada.get(), relogio.instant()));
    }

    public record PedidoDeCancelamento(String motivo) {
    }

    @DeleteMapping("/minha")
    public ResponseEntity<?> cancelar(@RequestBody(required = false) PedidoDeCancelamento pedido) {
        var motivo = pedido == null ? null : pedido.motivo();
        return Respostas.de(cancelar.executar(autenticado().contexto(), motivo),
                a -> Vistas.AssinaturaVista.de(a, relogio.instant()));
    }

    private static UsuarioAutenticado autenticado() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication();
    }
}
