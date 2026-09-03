package br.com.outorga.infrastructure.video;

import br.com.outorga.application.ports.EntregaDeVideo;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.infrastructure.config.ConfiguracaoDaOutorga;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.time.Duration;

/**
 * Entrega usada quando não ha bucket configurado.
 *
 * Devolve um HLS público de teste para que a plataforma inteira possa ser
 * demonstrada sem contratar armazenamento e sem hospedar obra de ninguém.
 * Toda a cadeia de decisão continua valendo: assinatura, licença, território,
 * classificação e limite de telas são verificados do mesmo jeito. O que muda e
 * só o arquivo que toca no fim.
 *
 * Não serve para produção, e o próprio sistema avisa isso em
 * {@code /actuator/info} e no painel.
 */
public class EntregaDeDemonstracao implements EntregaDeVideo {

    private final String amostra;
    private final Clock relogio;

    public EntregaDeDemonstracao(ConfiguracaoDaOutorga.Video config, Clock relogio) {
        this.amostra = config.amostraHls();
        this.relogio = relogio;
    }

    @Override
    public Result<EnderecoDeReproducao> assinarVod(String referenciaDoAtivo, Qualidade qualidade,
                                                   Duration validade) {
        if (referenciaDoAtivo == null || referenciaDoAtivo.isBlank()) {
            return Result.erro(new FalhaDeNegocio("VIDEO_INDISPONIVEL",
                    "O arquivo de vídeo ainda não está pronto"));
        }
        return Result.ok(new EnderecoDeReproducao(amostra, "HLS",
                relogio.instant().plus(validade), "demonstracao"));
    }

    @Override
    public Result<EnderecoDeReproducao> assinarAoVivo(String referenciaDoCanal, Duration validade) {
        if (referenciaDoCanal == null || referenciaDoCanal.isBlank()) {
            return Result.erro(new FalhaDeNegocio("CANAL_SEM_FONTE", "Canal sem fonte configurada"));
        }
        return Result.ok(new EnderecoDeReproducao(amostra, "HLS",
                relogio.instant().plus(validade), "demonstracao"));
    }
}
