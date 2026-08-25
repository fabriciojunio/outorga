package br.com.mirante.application.usecases.identity;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.CifradorDeSenha;
import br.com.mirante.application.ports.EmissorDeToken;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.identity.Email;
import br.com.mirante.domain.identity.Usuario;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Login por e-mail e senha.
 *
 * Detalhe que parece bobo e nao e: quando o e-mail nao existe, o cifrador
 * roda mesmo assim contra um hash descartavel. Sem isso, a diferenca de tempo
 * entre "usuario nao existe" e "senha errada" entrega a lista de assinantes
 * de qualquer cliente para quem medir o relogio.
 */
public class AutenticarUsuario {

    private static final String HASH_DE_COMPARACAO =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final Repositorios.DeUsuario usuarios;
    private final Repositorios.DeTenant tenants;
    private final CifradorDeSenha cifrador;
    private final EmissorDeToken emissor;
    private final Auditor auditor;
    private final Clock relogio;

    public AutenticarUsuario(Repositorios.DeUsuario usuarios, Repositorios.DeTenant tenants,
                             CifradorDeSenha cifrador, EmissorDeToken emissor, Auditor auditor,
                             Clock relogio) {
        this.usuarios = usuarios;
        this.tenants = tenants;
        this.cifrador = cifrador;
        this.emissor = emissor;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record Entrada(UUID tenantId, String email, String senha, String enderecoIp) {
    }

    public record Saida(UUID usuarioId, String nome, Set<String> papeis, EmissorDeToken.Par tokens) {
    }

    public Result<Saida> executar(Entrada entrada) {
        var agora = relogio.instant();

        var tenant = tenants.porId(entrada.tenantId());
        if (tenant.isEmpty()) {
            return Result.erro(new FalhaDeNegocio("SERVICO_NAO_ENCONTRADO",
                    "Servico nao encontrado"));
        }

        var email = Email.de(entrada.email());
        if (email.falhou()) {
            return Result.erro(new FalhaDeNegocio("CREDENCIAL_INVALIDA",
                    "E-mail ou senha incorretos"));
        }

        var achado = usuarios.porEmail(entrada.tenantId(), email.valorOuFalha());
        if (achado.isEmpty()) {
            cifrador.confere(entrada.senha(), HASH_DE_COMPARACAO);
            registrarRecusa(entrada, email.valorOuFalha().mascarado(), "usuario inexistente");
            return Result.erro(new FalhaDeNegocio("CREDENCIAL_INVALIDA",
                    "E-mail ou senha incorretos"));
        }

        Usuario usuario = achado.get();
        boolean senhaConfere = cifrador.confere(entrada.senha(), usuario.senhaHash());
        var tentativa = usuario.registrarTentativaDeLogin(senhaConfere, agora);
        usuarios.salvar(usuario);

        if (tentativa.falhou()) {
            registrarRecusa(entrada, usuario.email().mascarado(),
                    tentativa.falha().map(FalhaDeNegocio::codigo).orElse("desconhecido"));
            return Result.erro(tentativa.falha().orElseThrow());
        }

        var tokens = emissor.emitir(usuario.tenantId(), usuario.id(), usuario.papeis());
        auditor.registrar(
                new ContextoDoChamador(usuario.tenantId(), usuario.id(), usuario.email().mascarado(),
                        usuario.papeis(), entrada.enderecoIp()),
                AcaoAuditavel.LOGIN_OK, "usuario", usuario.id().toString());

        return Result.ok(new Saida(usuario.id(), usuario.nome(),
                usuario.papeis().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()),
                tokens));
    }

    private void registrarRecusa(Entrada entrada, String emailMascarado, String motivo) {
        auditor.registrar(
                new ContextoDoChamador(entrada.tenantId(), null, emailMascarado, Set.of(),
                        entrada.enderecoIp()),
                AcaoAuditavel.LOGIN_RECUSADO, "usuario", null, Map.of("motivo", motivo));
    }
}
