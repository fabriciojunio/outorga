package br.com.outorga.infrastructure.video;

import br.com.outorga.application.ports.EntregaDeVideo;
import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.infrastructure.config.ConfiguracaoDaOutorga;
import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.time.Duration;

/**
 * Entrega de VOD a partir de um bucket compativel com S3, com URL assinada e
 * de vida curta.
 *
 * A convencao de chave e {@code <referencia>/<altura>p/playlist.m3u8}, com um
 * mestre em {@code <referencia>/mestre.m3u8}. Quando o plano limita a
 * qualidade, o servidor entrega a renditura daquele teto em vez do mestre, e
 * assim o limite de plano vale de verdade: nao adianta o player pedir 4K se o
 * manifesto entregue nao tem 4K dentro.
 */
public class EntregaEmObjectStorage implements EntregaDeVideo {

    private final AssinaturaS3 assinatura;
    private final Clock relogio;

    public EntregaEmObjectStorage(ConfiguracaoDaOutorga.Video config, Clock relogio) {
        this.assinatura = new AssinaturaS3(config.endpoint(), config.bucket(), config.chaveDeAcesso(),
                config.segredo(), config.regiao());
        this.relogio = relogio;
    }

    @Override
    public Result<EnderecoDeReproducao> assinarVod(String referenciaDoAtivo, Qualidade qualidade,
                                                   Duration validade) {
        if (referenciaDoAtivo == null || referenciaDoAtivo.isBlank()) {
            return Result.erro(new FalhaDeNegocio("VIDEO_INDISPONIVEL",
                    "O arquivo de video ainda nao esta pronto"));
        }
        var agora = relogio.instant();
        var chave = referenciaDoAtivo + "/" + qualidade.alturaMaxima() + "p/playlist.m3u8";
        var url = assinatura.preAssinarGet(chave, validade, agora);
        return Result.ok(new EnderecoDeReproducao(url, "HLS", agora.plus(validade), null));
    }

    @Override
    public Result<EnderecoDeReproducao> assinarAoVivo(String referenciaDoCanal, Duration validade) {
        // Canal linear nao passa por bucket: a fonte e continua e o
        // empacotador entrega o manifesto. Enquanto o ingest proprio nao
        // existe, o endereco cadastrado no canal e usado como esta.
        if (referenciaDoCanal == null || referenciaDoCanal.isBlank()) {
            return Result.erro(new FalhaDeNegocio("CANAL_SEM_FONTE",
                    "Canal sem fonte configurada"));
        }
        return Result.ok(new EnderecoDeReproducao(referenciaDoCanal, "HLS",
                relogio.instant().plus(validade), null));
    }
}
