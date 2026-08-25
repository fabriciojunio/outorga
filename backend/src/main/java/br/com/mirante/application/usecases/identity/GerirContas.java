package br.com.mirante.application.usecases.identity;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.CifradorDeSenha;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.domain.identity.Dispositivo;
import br.com.mirante.domain.identity.Email;
import br.com.mirante.domain.identity.Papel;
import br.com.mirante.domain.identity.Perfil;
import br.com.mirante.domain.identity.Usuario;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro de conta, perfis e aparelhos. Reune o que o assinante faz na
 * propria conta e o que o painel faz com operadores.
 */
public class GerirContas {

    private static final int SENHA_MINIMA = 10;

    private final Repositorios.DeUsuario usuarios;
    private final Repositorios.DePerfil perfis;
    private final Repositorios.DeDispositivo dispositivos;
    private final CifradorDeSenha cifrador;
    private final Auditor auditor;
    private final Clock relogio;

    public GerirContas(Repositorios.DeUsuario usuarios, Repositorios.DePerfil perfis,
                       Repositorios.DeDispositivo dispositivos, CifradorDeSenha cifrador,
                       Auditor auditor, Clock relogio) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.dispositivos = dispositivos;
        this.cifrador = cifrador;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record NovaConta(String nome, String email, String senha, Set<Papel> papeis) {
    }

    public Result<Usuario> criar(ContextoDoChamador chamador, NovaConta nova) {
        boolean criandoOperador = nova.papeis().stream().anyMatch(Papel::acessaPainel);
        if (criandoOperador && !chamador.tem(Papel.DONO) && !chamador.tem(Papel.ADMIN_PLATAFORMA)) {
            return Result.erro(Falhas.semPermissao("criar usuario de painel"));
        }
        if (nova.papeis().contains(Papel.ADMIN_PLATAFORMA) && !chamador.tem(Papel.ADMIN_PLATAFORMA)) {
            return Result.erro(Falhas.semPermissao("criar administrador de plataforma"));
        }
        if (nova.senha() == null || nova.senha().length() < SENHA_MINIMA) {
            return Result.erro(Falhas.invalido(
                    "A senha precisa ter ao menos " + SENHA_MINIMA + " caracteres"));
        }

        var email = Email.de(nova.email());
        if (email.falhou()) {
            return Result.erro(email.falha().orElseThrow());
        }
        if (usuarios.existeEmail(chamador.tenantId(), email.valorOuFalha())) {
            return Result.erro(Falhas.conflito("Ja existe conta com este e-mail"));
        }

        var criacao = Usuario.criar(chamador.tenantId(), email.valorOuFalha(),
                cifrador.cifrar(nova.senha()), nova.nome(), nova.papeis(), relogio.instant());
        if (criacao.falhou()) {
            return criacao;
        }
        var usuario = usuarios.salvar(criacao.valorOuFalha());
        auditor.registrar(chamador, AcaoAuditavel.USUARIO_CRIADO, "usuario", usuario.id().toString(),
                Map.of("papeis", nova.papeis().toString()));
        return Result.ok(usuario);
    }

    public Result<Perfil> criarPerfil(ContextoDoChamador chamador, String nome,
                                      ClassificacaoIndicativa teto, boolean infantil, String pin) {
        var criacao = Perfil.criar(chamador.usuarioId(), nome, teto, infantil,
                perfis.quantidadeDoUsuario(chamador.usuarioId()));
        if (criacao.falhou()) {
            return criacao;
        }
        var perfil = criacao.valorOuFalha();
        if (pin != null && !pin.isBlank()) {
            perfil.definirPin(cifrador.cifrar(pin));
        }
        return Result.ok(perfis.salvar(perfil));
    }

    public List<Perfil> perfis(ContextoDoChamador chamador) {
        return perfis.doUsuario(chamador.usuarioId());
    }

    public List<Dispositivo> dispositivos(ContextoDoChamador chamador) {
        return dispositivos.doUsuario(chamador.usuarioId());
    }

    /**
     * Remover aparelho e a valvula de escape do limite de dispositivos. So o
     * dono da conta remove os proprios.
     */
    public Result<String> removerDispositivo(ContextoDoChamador chamador, UUID dispositivoId) {
        var meus = dispositivos.doUsuario(chamador.usuarioId());
        var alvo = meus.stream().filter(d -> d.id().equals(dispositivoId)).findFirst();
        if (alvo.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Dispositivo"));
        }
        dispositivos.remover(dispositivoId);
        return Result.ok("Dispositivo removido");
    }

    public Result<String> trocarSenha(ContextoDoChamador chamador, String senhaAtual, String novaSenha) {
        if (novaSenha == null || novaSenha.length() < SENHA_MINIMA) {
            return Result.erro(Falhas.invalido(
                    "A senha precisa ter ao menos " + SENHA_MINIMA + " caracteres"));
        }
        var achado = usuarios.porId(chamador.tenantId(), chamador.usuarioId());
        if (achado.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Usuario"));
        }
        var usuario = achado.get();
        if (!cifrador.confere(senhaAtual, usuario.senhaHash())) {
            return Result.erro(Falhas.invalido("Senha atual incorreta"));
        }
        var troca = usuario.trocarSenha(cifrador.cifrar(novaSenha));
        if (troca.falhou()) {
            return Result.erro(troca.falha().orElseThrow());
        }
        usuarios.salvar(usuario);
        auditor.registrar(chamador, AcaoAuditavel.SENHA_TROCADA, "usuario", usuario.id().toString());
        return Result.ok("Senha alterada");
    }
}
