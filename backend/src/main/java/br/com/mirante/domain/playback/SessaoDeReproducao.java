package br.com.mirante.domain.playback;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Sessao aberta de reproducao. E o que sustenta o limite de telas.
 *
 * O player manda um sinal de vida periodico. Sessao sem sinal por mais que
 * {@link #TOLERANCIA} e considerada morta, porque fechar o app na marra e
 * perder a rede sao a regra, nao a excecao, e ninguem quer ligar para o
 * suporte porque o celular que caiu ontem ainda ocupa uma tela.
 */
public class SessaoDeReproducao {

    public static final Duration TOLERANCIA = Duration.ofMinutes(2);

    private final UUID id;
    private final UUID tenantId;
    private final UUID usuarioId;
    private final UUID perfilId;
    private final UUID tituloId;
    private final String dispositivoId;
    private final Instant abertaEm;
    private Instant ultimoSinal;
    private Instant fechadaEm;
    private long posicaoEmSegundos;

    private SessaoDeReproducao(UUID id, UUID tenantId, UUID usuarioId, UUID perfilId, UUID tituloId,
                               String dispositivoId, Instant abertaEm) {
        this.id = id;
        this.tenantId = tenantId;
        this.usuarioId = usuarioId;
        this.perfilId = perfilId;
        this.tituloId = tituloId;
        this.dispositivoId = dispositivoId;
        this.abertaEm = abertaEm;
        this.ultimoSinal = abertaEm;
    }

    public static SessaoDeReproducao abrir(Autorizacao autorizacao, UUID usuarioId,
                                           String dispositivoId, Instant agora) {
        return new SessaoDeReproducao(autorizacao.sessaoId(), autorizacao.tenantId(), usuarioId,
                autorizacao.perfilId(), autorizacao.tituloId(), dispositivoId, agora);
    }

    public void sinalDeVida(long posicaoEmSegundos, Instant agora) {
        this.ultimoSinal = agora;
        if (posicaoEmSegundos >= 0) {
            this.posicaoEmSegundos = posicaoEmSegundos;
        }
    }

    public void fechar(Instant agora) {
        this.fechadaEm = agora;
    }

    public boolean viva(Instant agora) {
        return fechadaEm == null && !ultimoSinal.plus(TOLERANCIA).isBefore(agora);
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public UUID usuarioId() { return usuarioId; }

    public UUID perfilId() { return perfilId; }

    public UUID tituloId() { return tituloId; }

    public String dispositivoId() { return dispositivoId; }

    public Instant abertaEm() { return abertaEm; }

    public Instant ultimoSinal() { return ultimoSinal; }

    public Instant fechadaEm() { return fechadaEm; }

    public long posicaoEmSegundos() { return posicaoEmSegundos; }

    public static SessaoDeReproducao reconstituir(UUID id, UUID tenantId, UUID usuarioId, UUID perfilId,
                                                  UUID tituloId, String dispositivoId, Instant abertaEm,
                                                  Instant ultimoSinal, Instant fechadaEm,
                                                  long posicaoEmSegundos) {
        var sessao = new SessaoDeReproducao(id, tenantId, usuarioId, perfilId, tituloId, dispositivoId,
                abertaEm);
        sessao.ultimoSinal = ultimoSinal;
        sessao.fechadaEm = fechadaEm;
        sessao.posicaoEmSegundos = posicaoEmSegundos;
        return sessao;
    }
}
