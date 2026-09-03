package br.com.outorga.api;

import br.com.outorga.application.usecases.playback.AcompanharSessao;
import br.com.outorga.application.usecases.playback.AutorizarReproducao;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.domain.rights.TipoDeDispositivo;
import br.com.outorga.infrastructure.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Reprodução.
 *
 * O token devolvido aqui vale poucos minutos e vale para uma sessão só. O
 * player renova enquanto assiste; se o assinante perder o direito no meio do
 * filme, a proxima renovação já não sai.
 */
@RestController
@RequestMapping("/api/v1/reproducao")
public class ReproducaoController {

    private final AutorizarReproducao autorizar;
    private final AcompanharSessao sessoes;

    public ReproducaoController(AutorizarReproducao autorizar, AcompanharSessao sessoes) {
        this.autorizar = autorizar;
        this.sessoes = sessoes;
    }

    public record PedidoDePlay(
            @NotNull UUID tituloId,
            Integer temporada,
            Integer episodio,
            UUID perfilId,
            TipoDeDispositivo tipoDeDispositivo,
            String apelidoDoDispositivo,
            String territorio,
            Qualidade qualidade) {
    }

    @PostMapping("/token")
    public ResponseEntity<?> autorizar(@Valid @RequestBody PedidoDePlay pedido,
                                       @RequestHeader("X-Outorga-Dispositivo") String dispositivo) {
        var autenticado = autenticado();
        var entrada = new AutorizarReproducao.Entrada(
                pedido.tituloId(), pedido.temporada(), pedido.episodio(), pedido.perfilId(),
                dispositivo,
                pedido.tipoDeDispositivo() == null ? TipoDeDispositivo.WEB : pedido.tipoDeDispositivo(),
                pedido.apelidoDoDispositivo(), pedido.territorio(), pedido.qualidade());
        return Respostas.de(autorizar.executar(autenticado.contexto(), entrada));
    }

    public record Sinal(long posicaoEmSegundos) {
    }

    @PostMapping("/sessoes/{id}/sinal")
    public ResponseEntity<?> sinal(@PathVariable UUID id, @RequestBody Sinal sinal) {
        return Respostas.de(
                sessoes.sinalDeVida(autenticado().contexto(), id, sinal.posicaoEmSegundos()),
                s -> java.util.Map.of("sessao", s.id(), "posicao", s.posicaoEmSegundos()));
    }

    @DeleteMapping("/sessoes/{id}")
    public ResponseEntity<?> encerrar(@PathVariable UUID id,
                                      @RequestBody(required = false) Sinal sinal) {
        long posicao = sinal == null ? -1 : sinal.posicaoEmSegundos();
        return Respostas.de(sessoes.encerrar(autenticado().contexto(), id, posicao),
                s -> java.util.Map.of("sessao", s.id(), "encerrada", true));
    }

    private static UsuarioAutenticado autenticado() {
        return (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication();
    }
}
