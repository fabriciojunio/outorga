package br.com.outorga.api;

import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.EmissorDeToken;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.application.usecases.identity.AutenticarUsuario;
import br.com.outorga.application.usecases.identity.GerirContas;
import br.com.outorga.domain.identity.Papel;
import br.com.outorga.infrastructure.security.FiltroDeAutenticacao;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Entrada no sistema. Todos os enderecos aqui sao publicos e por isso
 * carregam o identificador do servico no proprio corpo: sem token ainda nao
 * da para saber de qual cliente e a conta.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AutenticacaoController {

    private final AutenticarUsuario autenticar;
    private final GerirContas contas;
    private final EmissorDeToken emissor;
    private final Repositorios.DeTenant tenants;

    public AutenticacaoController(AutenticarUsuario autenticar, GerirContas contas,
                                  EmissorDeToken emissor, Repositorios.DeTenant tenants) {
        this.autenticar = autenticar;
        this.contas = contas;
        this.emissor = emissor;
        this.tenants = tenants;
    }

    public record PedidoDeLogin(
            @NotBlank String servico,
            @NotBlank @Email String email,
            @NotBlank String senha) {
    }

    public record Sessao(String acesso, String refresh, String expiraEm, UUID usuarioId,
                         String nome, Set<String> papeis) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody PedidoDeLogin pedido,
                                   HttpServletRequest requisicao) {
        var tenant = tenants.porSlug(pedido.servico());
        if (tenant.isEmpty()) {
            return Respostas.de(Result.<Sessao>erro(Falhas.naoEncontrado("Servico")));
        }
        var saida = autenticar.executar(new AutenticarUsuario.Entrada(
                tenant.get().id(), pedido.email(), pedido.senha(),
                FiltroDeAutenticacao.ipDe(requisicao)));

        return Respostas.de(saida, s -> new Sessao(
                s.tokens().acesso(), s.tokens().refresh(),
                s.tokens().acessoExpiraEm().toString(), s.usuarioId(), s.nome(), s.papeis()));
    }

    public record PedidoDeRenovacao(@NotBlank String refresh) {
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> renovar(@Valid @RequestBody PedidoDeRenovacao pedido) {
        return Respostas.de(emissor.renovar(pedido.refresh()), par -> new Sessao(
                par.acesso(), par.refresh(), par.acessoExpiraEm().toString(), null, null, Set.of()));
    }

    public record PedidoDeCadastro(
            @NotBlank String servico,
            @NotBlank String nome,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 10, message = "a senha precisa de ao menos 10 caracteres")
            String senha) {
    }

    /**
     * Cadastro de espectador. Nasce sempre como ASSINANTE; papel de painel so
     * sai de dentro do painel, nunca de um endereco publico.
     */
    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@Valid @RequestBody PedidoDeCadastro pedido,
                                       HttpServletRequest requisicao) {
        var tenant = tenants.porSlug(pedido.servico());
        if (tenant.isEmpty()) {
            return Respostas.de(Result.erro(Falhas.naoEncontrado("Servico")));
        }
        var chamador = new ContextoDoChamador(tenant.get().id(), null, pedido.email(),
                Set.of(Papel.ASSINANTE), FiltroDeAutenticacao.ipDe(requisicao));

        var criado = contas.criar(chamador,
                new GerirContas.NovaConta(pedido.nome(), pedido.email(), pedido.senha(),
                        Set.of(Papel.ASSINANTE)));

        return Respostas.criado(criado, usuario -> {
            var tokens = emissor.emitir(usuario.tenantId(), usuario.id(), usuario.papeis());
            return new Sessao(tokens.acesso(), tokens.refresh(), tokens.acessoExpiraEm().toString(),
                    usuario.id(), usuario.nome(),
                    Set.of(Papel.ASSINANTE.name()));
        });
    }
}
