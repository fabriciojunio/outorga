package br.com.mirante.domain.live;

import br.com.mirante.domain.catalog.ClassificacaoIndicativa;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.time.Instant;
import java.util.UUID;

/**
 * Canal linear. Vale a mesma regra do catalogo: so vai ao ar com licenca
 * vigente, e a fonte precisa estar declarada como autorizada.
 *
 * A plataforma nao captura nem retransmite sinal de terceiro por conta
 * propria. A fonte e informada pelo cliente, que declara ter o direito, e o
 * cadastro da licenca e o que libera a exibicao.
 */
public class CanalAoVivo {

    private final UUID id;
    private final UUID tenantId;
    private String nome;
    private String logoUri;
    private int numero;
    private String urlDaFonte;
    private ClassificacaoIndicativa classificacao;
    private UUID licencaId;
    private boolean noAr;
    private String motivoDoBloqueio;
    /**
     * Distingue "a varredura tirou por falta de direito" de "o operador tirou
     * de proposito". Sem essa diferenca, um canal em manutencao voltaria ao ar
     * sozinho na proxima varredura, contra a vontade de quem o desligou.
     */
    private boolean bloqueadoPorDireito;

    private CanalAoVivo(UUID id, UUID tenantId, String nome, int numero,
                        ClassificacaoIndicativa classificacao) {
        this.id = id;
        this.tenantId = tenantId;
        this.nome = nome;
        this.numero = numero;
        this.classificacao = classificacao;
    }

    public static Result<CanalAoVivo> cadastrar(UUID tenantId, String nome, int numero,
                                                ClassificacaoIndicativa classificacao) {
        if (tenantId == null) {
            return Result.erro(new FalhaDeNegocio("CANAL_SEM_TENANT",
                    "Canal precisa pertencer a um tenant"));
        }
        if (nome == null || nome.isBlank()) {
            return Result.erro(new FalhaDeNegocio("CANAL_SEM_NOME", "Informe o nome do canal"));
        }
        if (numero < 1) {
            return Result.erro(new FalhaDeNegocio("CANAL_NUMERO_INVALIDO",
                    "O numero do canal comeca em 1"));
        }
        if (classificacao == null) {
            return Result.erro(new FalhaDeNegocio("CANAL_SEM_CLASSIFICACAO",
                    "Informe a classificacao indicativa do canal"));
        }
        return Result.ok(new CanalAoVivo(UUID.randomUUID(), tenantId, nome.trim(), numero, classificacao));
    }

    public Result<CanalAoVivo> definirFonte(String url) {
        if (url == null || url.isBlank()) {
            return Result.erro(new FalhaDeNegocio("FONTE_VAZIA",
                    "Informe a URL da fonte autorizada"));
        }
        var limpa = url.trim();
        if (!limpa.startsWith("https://") && !limpa.startsWith("rtmps://") && !limpa.startsWith("srt://")) {
            return Result.erro(new FalhaDeNegocio("FONTE_INSEGURA",
                    "A fonte precisa usar https, rtmps ou srt"));
        }
        this.urlDaFonte = limpa;
        return Result.ok(this);
    }

    public Result<CanalAoVivo> colocarNoAr(Licenca licenca, Instant agora) {
        if (urlDaFonte == null) {
            return Result.erro(new FalhaDeNegocio("CANAL_SEM_FONTE",
                    "Cadastre a fonte antes de colocar o canal no ar"));
        }
        if (licenca == null || !licenca.tenantId().equals(tenantId)) {
            return Result.erro(new FalhaDeNegocio("LICENCA_INVALIDA",
                    "Vincule uma licenca deste tenant ao canal"));
        }
        if (!licenca.vigenteEm(agora)) {
            return Result.erro(new FalhaDeNegocio("LICENCA_NAO_VIGENTE",
                    "A licenca do canal nao esta vigente"));
        }
        this.licencaId = licenca.id();
        this.noAr = true;
        this.motivoDoBloqueio = null;
        this.bloqueadoPorDireito = false;
        return Result.ok(this);
    }

    public boolean revisarDireitos(Licenca licenca, Instant agora) {
        boolean vigente = licenca != null && licenca.vigenteEm(agora);
        if (noAr && !vigente) {
            this.noAr = false;
            this.bloqueadoPorDireito = true;
            this.motivoDoBloqueio = "Licenca do canal sem vigencia";
            return true;
        }
        if (!noAr && bloqueadoPorDireito && vigente && urlDaFonte != null) {
            this.noAr = true;
            this.bloqueadoPorDireito = false;
            this.motivoDoBloqueio = null;
            return true;
        }
        return false;
    }

    public Result<CanalAoVivo> tirarDoAr(String motivo) {
        if (!noAr) {
            return Result.erro(new FalhaDeNegocio("CANAL_JA_FORA_DO_AR", "O canal ja esta fora do ar"));
        }
        this.noAr = false;
        this.bloqueadoPorDireito = false;
        this.motivoDoBloqueio = motivo;
        return Result.ok(this);
    }

    public boolean visivelPara(ClassificacaoIndicativa tetoDoPerfil) {
        return classificacao.liberadaPara(tetoDoPerfil);
    }

    public void definirLogo(String logoUri) { this.logoUri = logoUri; }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public String nome() { return nome; }

    public String logoUri() { return logoUri; }

    public int numero() { return numero; }

    public String urlDaFonte() { return urlDaFonte; }

    public ClassificacaoIndicativa classificacao() { return classificacao; }

    public UUID licencaId() { return licencaId; }

    public boolean noAr() { return noAr; }

    public String motivoDoBloqueio() { return motivoDoBloqueio; }

    public boolean bloqueadoPorDireito() { return bloqueadoPorDireito; }

    public static CanalAoVivo reconstituir(UUID id, UUID tenantId, String nome, String logoUri, int numero,
                                           String urlDaFonte, ClassificacaoIndicativa classificacao,
                                           UUID licencaId, boolean noAr, String motivoDoBloqueio,
                                           boolean bloqueadoPorDireito) {
        var canal = new CanalAoVivo(id, tenantId, nome, numero, classificacao);
        canal.logoUri = logoUri;
        canal.urlDaFonte = urlDaFonte;
        canal.licencaId = licencaId;
        canal.noAr = noAr;
        canal.motivoDoBloqueio = motivoDoBloqueio;
        canal.bloqueadoPorDireito = bloqueadoPorDireito;
        return canal;
    }
}
