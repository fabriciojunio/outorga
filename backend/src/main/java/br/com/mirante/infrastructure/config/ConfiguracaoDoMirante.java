package br.com.mirante.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuracao da aplicacao, validada na subida. Servidor com configuracao
 * incompleta nao sobe pela metade: recusa iniciar e diz o que falta.
 *
 * O campo {@code modo} decide o que acontece quando nao ha credencial de
 * fornecedor. Em DEMONSTRACAO o sistema roda inteiro com adaptadores locais,
 * sem gastar um centavo e sem chamar ninguem de fora. E o que permite ter o
 * produto no ar antes de existir contrato com gateway ou com CDN.
 */
@Validated
@ConfigurationProperties(prefix = "mirante")
public record ConfiguracaoDoMirante(
        Modo modo,
        Autenticacao autenticacao,
        Video video,
        Cobranca cobranca,
        String urlPublicaDaApi) {

    public enum Modo {
        /** Sem fornecedor externo. Video de amostra e cobranca simulada. */
        DEMONSTRACAO,
        /** Fornecedores reais configurados. */
        PRODUCAO
    }

    public ConfiguracaoDoMirante {
        modo = modo == null ? Modo.DEMONSTRACAO : modo;
        urlPublicaDaApi = urlPublicaDaApi == null ? "http://localhost:8080" : urlPublicaDaApi;
    }

    public boolean emDemonstracao() {
        return modo == Modo.DEMONSTRACAO;
    }

    /**
     * @param segredo        chave de assinatura do JWT. Curta demais e o mesmo
     *                       que nao ter, entao o tamanho minimo e cobrado aqui
     * @param validadeAcesso vida do token de acesso
     * @param validadeRefresh vida do refresh, que rotaciona a cada uso
     */
    public record Autenticacao(
            @NotBlank @Size(min = 64, message = "o segredo do JWT precisa de ao menos 64 caracteres")
            String segredo,
            Duration validadeAcesso,
            Duration validadeRefresh) {

        public Autenticacao {
            validadeAcesso = validadeAcesso == null ? Duration.ofMinutes(15) : validadeAcesso;
            validadeRefresh = validadeRefresh == null ? Duration.ofDays(14) : validadeRefresh;
        }
    }

    /**
     * Entrega de video. O padrao e um bucket compativel com S3; a escolha de
     * partida e o Cloudflare R2, que nao cobra saida de dados e por isso
     * mantem o custo previsivel quando o trafego cresce.
     */
    public record Video(
            String endpoint,
            String bucket,
            String chaveDeAcesso,
            String segredo,
            String regiao,
            String urlPublica,
            String amostraHls) {

        public Video {
            regiao = regiao == null || regiao.isBlank() ? "auto" : regiao;
            amostraHls = amostraHls == null || amostraHls.isBlank()
                    ? "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                    : amostraHls;
        }

        public boolean configurado() {
            return endpoint != null && !endpoint.isBlank()
                    && bucket != null && !bucket.isBlank()
                    && chaveDeAcesso != null && !chaveDeAcesso.isBlank()
                    && segredo != null && !segredo.isBlank();
        }
    }

    /**
     * Cobranca. A implementacao de partida e o Asaas, escolhido por nao ter
     * mensalidade: enquanto nao entra dinheiro, nao sai.
     */
    public record Cobranca(
            String urlBase,
            String chaveDeApi,
            String segredoDoWebhook) {

        public Cobranca {
            urlBase = urlBase == null || urlBase.isBlank()
                    ? "https://api-sandbox.asaas.com/v3" : urlBase;
        }

        public boolean configurado() {
            return chaveDeApi != null && !chaveDeApi.isBlank();
        }
    }
}
