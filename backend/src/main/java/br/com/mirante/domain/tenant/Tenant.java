package br.com.mirante.domain.tenant;

import br.com.mirante.shared.FalhaDeNegocio;
import br.com.mirante.shared.Result;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cliente da plataforma: quem contrata o Mirante para rodar o proprio
 * servico de streaming. Todo dado do sistema pendura em um tenant.
 */
public class Tenant {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]([a-z0-9-]{1,30}[a-z0-9])$");

    private final UUID id;
    private final String slug;
    private String nome;
    private String documento;
    private String dominioProprio;
    private Marca marca;
    private StatusDoTenant status;
    private final Instant criadoEm;
    private Instant fimDoTeste;
    private String motivoDaSuspensao;

    private Tenant(UUID id, String slug, String nome, String documento, Marca marca, Instant criadoEm) {
        this.id = id;
        this.slug = slug;
        this.nome = nome;
        this.documento = documento;
        this.marca = marca;
        this.status = StatusDoTenant.EM_IMPLANTACAO;
        this.criadoEm = criadoEm;
    }

    public static Result<Tenant> abrir(String slug, String nome, String documento, Marca marca, Instant agora) {
        if (nome == null || nome.isBlank()) {
            return Result.erro(new FalhaDeNegocio("TENANT_SEM_NOME", "Informe o nome do cliente"));
        }
        String normalizado = slug == null ? "" : slug.trim().toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(normalizado).matches()) {
            return Result.erro(new FalhaDeNegocio("TENANT_SLUG_INVALIDO",
                    "O identificador aceita letras minusculas, numeros e hifen, de 3 a 32 caracteres"));
        }
        return Result.ok(new Tenant(UUID.randomUUID(), normalizado, nome.trim(), documento,
                marca == null ? Marca.padrao(nome.trim()) : marca, agora));
    }

    public Result<Tenant> liberarParaProducao() {
        if (status == StatusDoTenant.ENCERRADO) {
            return Result.erro(new FalhaDeNegocio("TENANT_ENCERRADO",
                    "Cliente encerrado nao volta a operar sem novo contrato"));
        }
        this.status = StatusDoTenant.ATIVO;
        this.motivoDaSuspensao = null;
        return Result.ok(this);
    }

    public Result<Tenant> suspender(String motivo) {
        if (status == StatusDoTenant.ENCERRADO) {
            return Result.erro(new FalhaDeNegocio("TENANT_ENCERRADO", "Cliente ja encerrado"));
        }
        this.status = StatusDoTenant.SUSPENSO;
        this.motivoDaSuspensao = motivo;
        return Result.ok(this);
    }

    public Result<Tenant> encerrar() {
        this.status = StatusDoTenant.ENCERRADO;
        return Result.ok(this);
    }

    /** Espectador so entra quando o cliente esta ativo ou em periodo de teste. */
    public boolean aceitaTrafegoDeEspectador(Instant agora) {
        return switch (status) {
            case ATIVO -> true;
            case EM_IMPLANTACAO -> fimDoTeste != null && agora.isBefore(fimDoTeste);
            case SUSPENSO, ENCERRADO -> false;
        };
    }

    public void definirPeriodoDeTeste(Instant fim) { this.fimDoTeste = fim; }

    public void definirMarca(Marca marca) { this.marca = marca; }

    public void definirDominioProprio(String dominio) {
        this.dominioProprio = dominio == null ? null : dominio.trim().toLowerCase(Locale.ROOT);
    }

    public UUID id() { return id; }

    public String slug() { return slug; }

    public String nome() { return nome; }

    public String documento() { return documento; }

    public String dominioProprio() { return dominioProprio; }

    public Marca marca() { return marca; }

    public StatusDoTenant status() { return status; }

    public Instant criadoEm() { return criadoEm; }

    public Instant fimDoTeste() { return fimDoTeste; }

    public String motivoDaSuspensao() { return motivoDaSuspensao; }

    public static Tenant reconstituir(UUID id, String slug, String nome, String documento,
                                      String dominioProprio, Marca marca, StatusDoTenant status,
                                      Instant criadoEm, Instant fimDoTeste,
                                      String motivoDaSuspensao) {
        var tenant = new Tenant(id, slug, nome, documento, marca, criadoEm);
        tenant.dominioProprio = dominioProprio;
        tenant.status = status;
        tenant.fimDoTeste = fimDoTeste;
        tenant.motivoDaSuspensao = motivoDaSuspensao;
        return tenant;
    }
}
