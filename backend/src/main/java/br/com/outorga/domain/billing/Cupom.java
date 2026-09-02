package br.com.outorga.domain.billing;

import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public class Cupom {

    private final UUID id;
    private final UUID tenantId;
    private final String codigo;
    private final int percentual;
    private final Instant validoAte;
    private final int usosMaximos;
    private int usos;
    private boolean ativo;

    private Cupom(UUID id, UUID tenantId, String codigo, int percentual, Instant validoAte,
                  int usosMaximos) {
        this.id = id;
        this.tenantId = tenantId;
        this.codigo = codigo;
        this.percentual = percentual;
        this.validoAte = validoAte;
        this.usosMaximos = usosMaximos;
        this.ativo = true;
    }

    public static Result<Cupom> criar(UUID tenantId, String codigo, int percentual, Instant validoAte,
                                      int usosMaximos) {
        if (codigo == null || codigo.isBlank()) {
            return Result.erro(new FalhaDeNegocio("CUPOM_SEM_CODIGO", "Informe o codigo do cupom"));
        }
        if (percentual < 1 || percentual > 100) {
            return Result.erro(new FalhaDeNegocio("CUPOM_PERCENTUAL_INVALIDO",
                    "Desconto entre 1 e 100 por cento"));
        }
        if (usosMaximos < 1) {
            return Result.erro(new FalhaDeNegocio("CUPOM_SEM_USOS",
                    "Informe quantas vezes o cupom pode ser usado"));
        }
        return Result.ok(new Cupom(UUID.randomUUID(), tenantId,
                codigo.trim().toUpperCase(Locale.ROOT), percentual, validoAte, usosMaximos));
    }

    public Result<Cupom> resgatar(Instant agora) {
        if (!ativo) {
            return Result.erro(new FalhaDeNegocio("CUPOM_INATIVO", "Cupom desativado"));
        }
        if (validoAte != null && !agora.isBefore(validoAte)) {
            return Result.erro(new FalhaDeNegocio("CUPOM_VENCIDO", "Cupom fora do prazo"));
        }
        if (usos >= usosMaximos) {
            return Result.erro(new FalhaDeNegocio("CUPOM_ESGOTADO", "Cupom ja atingiu o limite de uso"));
        }
        usos++;
        return Result.ok(this);
    }

    public void desativar() {
        this.ativo = false;
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public String codigo() { return codigo; }

    public int percentual() { return percentual; }

    public Instant validoAte() { return validoAte; }

    public int usosMaximos() { return usosMaximos; }

    public int usos() { return usos; }

    public boolean ativo() { return ativo; }

    public static Cupom reconstituir(UUID id, UUID tenantId, String codigo, int percentual,
                                     Instant validoAte, int usosMaximos, int usos, boolean ativo) {
        var cupom = new Cupom(id, tenantId, codigo, percentual, validoAte, usosMaximos);
        cupom.usos = usos;
        cupom.ativo = ativo;
        return cupom;
    }
}
