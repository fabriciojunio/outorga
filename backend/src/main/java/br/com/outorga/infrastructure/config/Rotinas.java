package br.com.outorga.infrastructure.config;

import br.com.outorga.application.usecases.billing.EncerrarAssinaturasVencidas;
import br.com.outorga.application.usecases.playback.AcompanharSessao;
import br.com.outorga.application.usecases.rights.RevisarDireitosVigentes;
import br.com.outorga.infrastructure.security.EmissorDeTokenJwt;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotinas de fundo.
 *
 * Cada uma existe porque alguma coisa no sistema depende da passagem do tempo
 * e ninguém vai clicar em nada para que ela aconteca. A varredura de direitos
 * e a mais importante das quatro: e ela que faz o gate de conteúdo valer
 * depois que a licença vence, e não só no dia em que alguém cadastrou.
 */
@Component
public class Rotinas {

    private static final Logger log = LoggerFactory.getLogger(Rotinas.class);

    private final RevisarDireitosVigentes revisarDireitos;
    private final EncerrarAssinaturasVencidas encerrarAssinaturas;
    private final AcompanharSessao sessoes;
    private final EmissorDeTokenJwt emissor;
    private final MeterRegistry metricas;

    public Rotinas(RevisarDireitosVigentes revisarDireitos,
                   EncerrarAssinaturasVencidas encerrarAssinaturas,
                   AcompanharSessao sessoes, EmissorDeTokenJwt emissor, MeterRegistry metricas) {
        this.revisarDireitos = revisarDireitos;
        this.encerrarAssinaturas = encerrarAssinaturas;
        this.sessoes = sessoes;
        this.emissor = emissor;
        this.metricas = metricas;
    }

    /**
     * De hora em hora, com um atraso inicial para não competir com a subida da
     * aplicacao. Roda também logo depois de subir, de propósito: instancia que
     * dormiu a noite inteira precisa acertar o catálogo antes de atender o
     * primeiro espectador da manha.
     */
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1H")
    public void conferirDireitos() {
        try {
            var resultado = revisarDireitos.executar();
            metricas.counter("outorga.direitos.titulos_bloqueados")
                    .increment(resultado.titulosBloqueados());
            metricas.counter("outorga.direitos.titulos_liberados")
                    .increment(resultado.titulosLiberados());
            if (resultado.houveMudanca()) {
                log.info("Varredura de direitos: {} títulos bloqueados, {} liberados, {} canais",
                        resultado.titulosBloqueados(), resultado.titulosLiberados(),
                        resultado.canaisAfetados());
            }
        } catch (Exception e) {
            log.error("Varredura de direitos falhou", e);
        }
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "America/Sao_Paulo")
    public void encerrarAssinaturasVencidas() {
        try {
            int encerradas = encerrarAssinaturas.executar();
            if (encerradas > 0) {
                log.info("Assinaturas encerradas por vencimento: {}", encerradas);
            }
        } catch (Exception e) {
            log.error("Encerramento de assinaturas falhou", e);
        }
    }

    /** Sessão sem sinal de vida não pode continuar ocupando uma tela do plano. */
    @Scheduled(fixedDelayString = "PT2M")
    public void limparSessoes() {
        try {
            sessoes.fecharAbandonadas();
        } catch (Exception e) {
            log.error("Limpeza de sessões falhou", e);
        }
    }

    @Scheduled(cron = "0 40 3 * * *", zone = "America/Sao_Paulo")
    public void limparTokens() {
        try {
            int removidos = emissor.limparVencidos();
            if (removidos > 0) {
                log.info("Refresh tokens vencidos removidos: {}", removidos);
            }
        } catch (Exception e) {
            log.error("Limpeza de tokens falhou", e);
        }
    }
}
