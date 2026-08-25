package br.com.mirante.infrastructure.video;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Assinatura SigV4 para URL pre-assinada em storage compativel com S3.
 *
 * Escrito na mao em vez de trazer o SDK da AWS. O SDK resolve um universo de
 * casos que este projeto nao tem, e custa dezenas de megabytes no jar e
 * memoria de processo que a instancia gratuita nao tem para dar. O que o
 * sistema precisa e de uma operacao so, GET pre-assinado, e ela cabe aqui.
 */
public final class AssinaturaS3 {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String ALGORITMO = "AWS4-HMAC-SHA256";
    private static final String SERVICO = "s3";
    private static final String SEM_CORPO = "UNSIGNED-PAYLOAD";

    private final String endpoint;
    private final String bucket;
    private final String chaveDeAcesso;
    private final String segredo;
    private final String regiao;

    public AssinaturaS3(String endpoint, String bucket, String chaveDeAcesso, String segredo,
                        String regiao) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
        this.chaveDeAcesso = chaveDeAcesso;
        this.segredo = segredo;
        this.regiao = regiao;
    }

    /**
     * URL de GET valida por um tempo curto. Depois do prazo o endereco vira
     * lixo, que e o ponto: link copiado do inspetor do navegador nao vira
     * distribuicao paralela.
     */
    public String preAssinarGet(String chaveDoObjeto, Duration validade, Instant agora) {
        String dataHora = DATA_HORA.format(agora);
        String data = DATA.format(agora);
        String escopo = data + "/" + regiao + "/" + SERVICO + "/aws4_request";
        String host = endpoint.replaceFirst("^https?://", "");
        String caminho = "/" + bucket + "/" + codificarCaminho(chaveDoObjeto);

        String consulta = "X-Amz-Algorithm=" + ALGORITMO
                + "&X-Amz-Credential=" + codificar(chaveDeAcesso + "/" + escopo)
                + "&X-Amz-Date=" + dataHora
                + "&X-Amz-Expires=" + validade.toSeconds()
                + "&X-Amz-SignedHeaders=host";

        String requisicaoCanonica = String.join("\n",
                "GET",
                caminho,
                consulta,
                "host:" + host + "\n",
                "host",
                SEM_CORPO);

        String paraAssinar = String.join("\n",
                ALGORITMO,
                dataHora,
                escopo,
                hexDeSha256(requisicaoCanonica));

        byte[] chave = derivarChave(data);
        String assinatura = paraHex(hmac(chave, paraAssinar));

        return endpoint + caminho + "?" + consulta + "&X-Amz-Signature=" + assinatura;
    }

    private byte[] derivarChave(String data) {
        byte[] passo = hmac(("AWS4" + segredo).getBytes(StandardCharsets.UTF_8), data);
        passo = hmac(passo, regiao);
        passo = hmac(passo, SERVICO);
        return hmac(passo, "aws4_request");
    }

    private static byte[] hmac(byte[] chave, String dado) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(dado.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("falha ao assinar a URL do video", e);
        }
    }

    private static String hexDeSha256(String texto) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return paraHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("falha ao calcular o resumo da requisicao", e);
        }
    }

    private static String paraHex(byte[] bytes) {
        var saida = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            saida.append(String.format(Locale.ROOT, "%02x", b));
        }
        return saida.toString();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** A barra separa segmentos e nao pode ser escapada; o resto pode. */
    private static String codificarCaminho(String chave) {
        var partes = chave.split("/");
        var saida = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) {
                saida.append('/');
            }
            saida.append(codificar(partes[i]).replace("%2F", "/"));
        }
        return saida.toString();
    }
}
