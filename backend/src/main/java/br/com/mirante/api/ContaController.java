package br.com.mirante.api;

import br.com.mirante.application.usecases.identity.AtenderTitularDeDados;
import br.com.mirante.application.usecases.identity.GerirContas;
import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.infrastructure.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A conta pela otica de quem assina: perfis, aparelhos, senha e os direitos
 * de titular de dados.
 *
 * Os dois ultimos enderecos existem por causa da LGPD e nao sao enfeite de
 * conformidade: o exportar devolve de fato o que esta guardado, e o apagar
 * anonimiza de verdade.
 */
@RestController
@RequestMapping("/api/v1/me")
public class ContaController {

    private final GerirContas contas;
    private final AtenderTitularDeDados titular;

    public ContaController(GerirContas contas, AtenderTitularDeDados titular) {
        this.contas = contas;
        this.titular = titular;
    }

    public record NovoPerfil(@NotBlank String nome, ClassificacaoIndicativa tetoDeClassificacao,
                             boolean infantil, String pin) {
    }

    @PostMapping("/perfis")
    public ResponseEntity<?> criarPerfil(@Valid @RequestBody NovoPerfil novo) {
        return Respostas.criado(contas.criarPerfil(contexto(), novo.nome(),
                        novo.tetoDeClassificacao(), novo.infantil(), novo.pin()),
                p -> new PerfilVisto(p.id(), p.nome(), p.tetoDeClassificacao().rotulo(),
                        p.infantil(), p.protegidoPorPin()));
    }

    public record PerfilVisto(UUID id, String nome, String tetoDeClassificacao, boolean infantil,
                              boolean comPin) {
    }

    @GetMapping("/perfis")
    public ResponseEntity<?> perfis() {
        return ResponseEntity.ok(contas.perfis(contexto()).stream()
                .map(p -> new PerfilVisto(p.id(), p.nome(), p.tetoDeClassificacao().rotulo(),
                        p.infantil(), p.protegidoPorPin()))
                .toList());
    }

    public record DispositivoVisto(UUID id, String apelido, String tipo, String ultimoUso) {
    }

    @GetMapping("/dispositivos")
    public ResponseEntity<?> dispositivos() {
        return ResponseEntity.ok(contas.dispositivos(contexto()).stream()
                .map(d -> new DispositivoVisto(d.id(), d.apelido(), d.tipo().name(),
                        String.valueOf(d.ultimoUso())))
                .toList());
    }

    @DeleteMapping("/dispositivos/{id}")
    public ResponseEntity<?> removerDispositivo(@PathVariable UUID id) {
        return Respostas.de(contas.removerDispositivo(contexto(), id),
                mensagem -> java.util.Map.of("resultado", mensagem));
    }

    public record TrocaDeSenha(@NotBlank String senhaAtual, @NotBlank String novaSenha) {
    }

    @PostMapping("/senha")
    public ResponseEntity<?> trocarSenha(@Valid @RequestBody TrocaDeSenha troca) {
        return Respostas.de(contas.trocarSenha(contexto(), troca.senhaAtual(), troca.novaSenha()),
                mensagem -> java.util.Map.of("resultado", mensagem));
    }

    @GetMapping("/meus-dados")
    public ResponseEntity<?> exportar() {
        return Respostas.de(titular.exportar(contexto()));
    }

    @DeleteMapping("/minha-conta")
    public ResponseEntity<?> apagar() {
        return Respostas.de(titular.apagar(contexto()),
                mensagem -> java.util.Map.of("resultado", mensagem));
    }

    private static br.com.mirante.application.ContextoDoChamador contexto() {
        return ((UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication()).contexto();
    }
}
