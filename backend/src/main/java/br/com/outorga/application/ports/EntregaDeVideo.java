package br.com.outorga.application.ports;

import br.com.outorga.domain.billing.Qualidade;
import br.com.outorga.shared.Result;

import java.time.Duration;
import java.time.Instant;

/**
 * Porta de entrega de video. O dominio guarda so a referencia do ativo; e
 * aqui que ela vira um endereco assinado e de vida curta.
 *
 * Existe para nao amarrar o produto a um fornecedor. A implementacao de
 * partida usa a Bunny Stream, mas trocar por Cloudflare Stream, Mux ou por um
 * pipeline proprio com FFmpeg e trocar a classe que implementa esta interface.
 */
public interface EntregaDeVideo {

    Result<EnderecoDeReproducao> assinarVod(String referenciaDoAtivo, Qualidade qualidade,
                                            Duration validade);

    Result<EnderecoDeReproducao> assinarAoVivo(String referenciaDoCanal, Duration validade);

    /**
     * @param manifesto URL do playlist HLS ou do manifesto DASH
     * @param formato   HLS ou DASH
     * @param expiraEm  quando o endereco deixa de valer
     */
    record EnderecoDeReproducao(String manifesto, String formato, Instant expiraEm,
                                String tokenDeSessao) {
    }
}
