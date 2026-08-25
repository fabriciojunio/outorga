package br.com.mirante.application.usecases.identity;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.EmissorDeToken;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * Direitos do titular previstos na LGPD: ver o que a plataforma guarda sobre
 * ele e pedir a exclusao.
 *
 * A exclusao aqui e anonimizacao, nao DELETE. Registro de pagamento e trilha
 * de auditoria tem obrigacao legal de guarda propria, e apagar a linha
 * inteira quebraria a conciliacao financeira do cliente. O que se apaga e o
 * que identifica a pessoa; o que fica e o fato contabil, sem dono.
 */
public class AtenderTitularDeDados {

    private final Repositorios.DeUsuario usuarios;
    private final Repositorios.DePerfil perfis;
    private final Repositorios.DeDispositivo dispositivos;
    private final Repositorios.DeAssinatura assinaturas;
    private final EmissorDeToken emissor;
    private final Auditor auditor;
    private final Clock relogio;

    public AtenderTitularDeDados(Repositorios.DeUsuario usuarios, Repositorios.DePerfil perfis,
                                 Repositorios.DeDispositivo dispositivos,
                                 Repositorios.DeAssinatura assinaturas, EmissorDeToken emissor,
                                 Auditor auditor, Clock relogio) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.dispositivos = dispositivos;
        this.assinaturas = assinaturas;
        this.emissor = emissor;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record Exportacao(String nome, String email, String criadoEm, String ultimoAcesso,
                             List<String> perfis, List<String> dispositivos,
                             Map<String, String> assinatura) {
    }

    public Result<Exportacao> exportar(ContextoDoChamador chamador) {
        var achado = usuarios.porId(chamador.tenantId(), chamador.usuarioId());
        if (achado.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Usuario"));
        }
        var usuario = achado.get();

        var assinatura = assinaturas.vigenteDoUsuario(chamador.tenantId(), usuario.id())
                .map(a -> Map.of(
                        "status", a.status().name(),
                        "iniciadaEm", String.valueOf(a.iniciadaEm()),
                        "fimDoCiclo", String.valueOf(a.fimDoCicloAtual())))
                .orElse(Map.of());

        auditor.registrar(chamador, AcaoAuditavel.DADO_PESSOAL_EXPORTADO, "usuario",
                usuario.id().toString());

        return Result.ok(new Exportacao(
                usuario.nome(),
                usuario.email().valor(),
                String.valueOf(usuario.criadoEm()),
                String.valueOf(usuario.ultimoAcesso()),
                perfis.doUsuario(usuario.id()).stream().map(p -> p.nome()).toList(),
                dispositivos.doUsuario(usuario.id()).stream()
                        .map(d -> d.apelido() + " (" + d.tipo() + ")").toList(),
                assinatura));
    }

    public Result<String> apagar(ContextoDoChamador chamador) {
        var achado = usuarios.porId(chamador.tenantId(), chamador.usuarioId());
        if (achado.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Usuario"));
        }
        var usuario = achado.get();

        var assinatura = assinaturas.vigenteDoUsuario(chamador.tenantId(), usuario.id());
        if (assinatura.isPresent() && assinatura.get().permiteAssistir(relogio.instant())) {
            return Result.erro(Falhas.conflito(
                    "Cancele a assinatura antes de pedir a exclusao da conta"));
        }

        var emailAntes = usuario.email().mascarado();
        perfis.doUsuario(usuario.id()).forEach(p -> perfis.remover(p.id()));
        dispositivos.doUsuario(usuario.id()).forEach(d -> dispositivos.remover(d.id()));
        usuario.anonimizar(relogio.instant());
        usuarios.salvar(usuario);
        emissor.revogar(usuario.id());

        auditor.registrar(chamador, AcaoAuditavel.DADO_PESSOAL_APAGADO, "usuario",
                usuario.id().toString(),
                Map.of("modo", "anonimizacao", "email", emailAntes));

        return Result.ok("Conta desativada e dados pessoais anonimizados");
    }
}
