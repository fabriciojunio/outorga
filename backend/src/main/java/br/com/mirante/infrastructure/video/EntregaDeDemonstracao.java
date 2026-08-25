package br.com.mirante.infrastructure.video;

import br.com.mirante.application.ports.EntregaDeVideo;
import br.com.mirante.domain.billing.Qualidade;
import br.com.mirante.infrastructure.config.ConfiguracaoDoMirante;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.time.Duration;

/**
 * Entrega usada quando nao ha bucket configurado.
 *
 * Devolve um HLS publico de teste para que a plataforma inteira possa ser
 * demonstrada sem contratar armazenamento e sem hospedar obra de ninguem.
 * Toda a cadeia de decisao continua valendo: assinatura, licenca, territorio,
 * classificacao e limite de telas sao verificados do mesmo jeito. O que muda e
 * so o arquivo que toca no fim.
 *
 * Nao serve para producao, e o proprio sistema avisa isso em
 * {@code /actuator/info} e no painel.
 */
public class EntregaDeDemonstracao implements EntregaDeVideo {

    private final String amostra;
    private final Clock relogio;

    public EntregaDeDemonstracao(ConfiguracaoDoMirante.Video config, Clock relogio) {
        this.amostra = config.amostraHls();
        this.relogio = relogio;
    }

    @Override
    public Result<EnderecoDeReproducao> assinarVod(String referenciaDoAtivo, Qualidade qualidade,
                                                   Duration validade) {
        if (referenciaDoAtivo == null || referenciaDoAtivo.isBlank()) {
            return Result.erro(new FalhaDeNegocio("VIDEO_INDISPONIVEL",
                    "O arquivo de video ainda nao esta pronto"));
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
