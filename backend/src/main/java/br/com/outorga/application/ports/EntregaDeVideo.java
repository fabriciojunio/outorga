package br.com.outorga.application.ports;

import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.shared.Result;

import java.time.Duration;
import java.time.Instant;

/**
 * Porta de entrega de vídeo. O dominio guarda só a referência do ativo; e
 * aqui que ela vira um endereço assinado e de vida curta.
 *
 * Existe para não amarrar o produto a um fornecedor. A implementacao de
 * partida usa a Bunny Stream, mas trocar por Cloudflare Stream, Mux ou por um
 * pipeline próprio com FFmpeg e trocar a classe que implementa está interface.
 */
public interface EntregaDeVideo {

    Result<EnderecoDeReproducao> assinarVod(String referenciaDoAtivo, Qualidade qualidade,
                                            Duration validade);

    Result<EnderecoDeReproducao> assinarAoVivo(String referenciaDoCanal, Duration validade);

    /**
     * @param manifesto URL do playlist HLS ou do manifesto DASH
     * @param formato   HLS ou DASH
     * @param expiraEm  quando o endereço deixa de valer
     */
    record EnderecoDeReproducao(String manifesto, String formato, Instant expiraEm,
                                String tokenDeSessao) {
    }
}
