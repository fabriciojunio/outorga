package br.com.outorga.application.usecases.playback;

import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.playback.SessaoDeReproducao;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.util.UUID;

/**
 * Sinal de vida e encerramento da sessão. O player chama o sinal a cada
 * trinta segundos; a posicao vem junto e serve de "continuar assistindo".
 */
public class AcompanharSessao {

    private final Repositorios.DeSessao sessoes;
    private final Clock relogio;

    public AcompanharSessao(Repositorios.DeSessao sessoes, Clock relogio) {
        this.sessoes = sessoes;
        this.relogio = relogio;
    }

    public Result<SessaoDeReproducao> sinalDeVida(ContextoDoChamador chamador, UUID sessaoId,
                                                  long posicaoEmSegundos) {
        var achada = carregarDoUsuario(chamador, sessaoId);
        if (achada.falhou()) {
            return achada;
        }
        var sessao = achada.valorOuFalha();
        sessao.sinalDeVida(posicaoEmSegundos, relogio.instant());
        return Result.ok(sessoes.salvar(sessao));
    }

    public Result<SessaoDeReproducao> encerrar(ContextoDoChamador chamador, UUID sessaoId,
                                               long posicaoEmSegundos) {
        var achada = carregarDoUsuario(chamador, sessaoId);
        if (achada.falhou()) {
            return achada;
        }
        var sessao = achada.valorOuFalha();
        sessao.sinalDeVida(posicaoEmSegundos, relogio.instant());
        sessao.fechar(relogio.instant());
        return Result.ok(sessoes.salvar(sessao));
    }

    /** Rotina de limpeza: sessão sem sinal ha tempo demais não ocupa tela. */
    public int fecharAbandonadas() {
        return sessoes.fecharAbandonadas(
                relogio.instant().minus(SessaoDeReproducao.TOLERANCIA));
    }

    private Result<SessaoDeReproducao> carregarDoUsuario(ContextoDoChamador chamador, UUID sessaoId) {
        var achada = sessoes.porId(sessaoId);
        if (achada.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Sessao"));
        }
        var sessao = achada.get();
        if (!sessao.tenantId().equals(chamador.tenantId())
                || !sessao.usuarioId().equals(chamador.usuarioId())) {
            return Result.erro(Falhas.naoEncontrado("Sessao"));
        }
        return Result.ok(sessao);
    }
}
