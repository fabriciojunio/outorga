package br.com.outorga.infrastructure.config;

import br.com.outorga.application.ports.EntregaDeVideo;
import br.com.outorga.application.ports.GatewayDePagamento;
import br.com.outorga.infrastructure.billing.GatewayAsaas;
import br.com.outorga.infrastructure.billing.GatewayDeDemonstracao;
import br.com.outorga.infrastructure.video.EntregaDeDemonstracao;
import br.com.outorga.infrastructure.video.EntregaEmObjectStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Escolha dos fornecedores externos.
 *
 * A regra e simples e vale para os dois: se estiver configurado, usa o de
 * verdade; se nao estiver e o sistema estiver em DEMONSTRACAO, usa o
 * simulado; se nao estiver e o sistema estiver em PRODUCAO, recusa subir.
 *
 * Esse ultimo caso e o que importa. Subir em producao com gateway simulado
 * significaria dar assinatura de graca para quem clicasse, e o erro so
 * apareceria na conciliacao. Falhar na inicializacao custa cinco minutos;
 * descobrir depois custa o mes inteiro.
 */
@Configuration
public class FornecedoresExternos {

    private static final Logger log = LoggerFactory.getLogger(FornecedoresExternos.class);

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    public EntregaDeVideo entregaDeVideo(ConfiguracaoDaOutorga configuracao, Clock relogio) {
        var video = configuracao.video();
        if (video != null && video.configurado()) {
            log.info("Entrega de video: bucket {} em {}", video.bucket(), video.endpoint());
            return new EntregaEmObjectStorage(video, relogio);
        }
        if (!configuracao.emDemonstracao()) {
            throw new IllegalStateException("""
                    Em producao e obrigatorio configurar o armazenamento de video \
                    (outorga.video.endpoint, .bucket, .chave-de-acesso e .segredo).""");
        }
        log.warn("Entrega de video em modo demonstracao: todo play devolve o HLS de amostra.");
        return new EntregaDeDemonstracao(
                video == null ? new ConfiguracaoDaOutorga.Video(null, null, null, null, null, null, null)
                        : video,
                relogio);
    }

    @Bean
    public GatewayDePagamento gatewayDePagamento(ConfiguracaoDaOutorga configuracao,
                                                 ObjectMapper json, Clock relogio) {
        var cobranca = configuracao.cobranca();
        if (cobranca != null && cobranca.configurado()) {
            log.info("Cobranca pelo gateway em {}", cobranca.urlBase());
            return new GatewayAsaas(cobranca, json, relogio);
        }
        if (!configuracao.emDemonstracao()) {
            throw new IllegalStateException("""
                    Em producao e obrigatorio configurar o gateway de cobranca \
                    (outorga.cobranca.chave-de-api e .segredo-do-webhook).""");
        }
        log.warn("Cobranca em modo demonstracao: nenhum dinheiro e movimentado.");
        return new GatewayDeDemonstracao(configuracao, json);
    }
}
