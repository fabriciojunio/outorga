package br.com.outorga.domain.rights;

import br.com.outorga.shared.FalhaDeNegocio;
import br.com.outorga.shared.Result;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Autorizacao de distribuicao de uma obra ou canal, com titular, contrato,
 * territorio, janela e comprovacao. E a peca que decide se algo pode ir ao ar.
 *
 * Regra da casa: sem licenca VIGENTE cobrindo hoje e o territorio do
 * espectador, nao existe reproducao. Nao ha caminho no dominio que contorne
 * isso, e por isso a checagem mora aqui e nao no controller.
 */
public class Licenca {

    private final UUID id;
    private final UUID tenantId;
    private final String titular;
    private final String referenciaDoContrato;
    private final Set<Territorio> territorios;
    private final JanelaDeLicenca janela;
    private final Set<TipoDeDispositivo> dispositivosAutorizados;
    private String comprovacaoUri;
    private StatusDaLicenca status;
    private String observacao;

    private Licenca(UUID id, UUID tenantId, String titular, String referenciaDoContrato,
                    Set<Territorio> territorios, JanelaDeLicenca janela,
                    Set<TipoDeDispositivo> dispositivosAutorizados) {
        this.id = id;
        this.tenantId = tenantId;
        this.titular = titular;
        this.referenciaDoContrato = referenciaDoContrato;
        this.territorios = new LinkedHashSet<>(territorios);
        this.janela = janela;
        this.dispositivosAutorizados = EnumSet.copyOf(dispositivosAutorizados);
        this.status = StatusDaLicenca.RASCUNHO;
    }

    public static Result<Licenca> cadastrar(UUID tenantId, String titular, String referenciaDoContrato,
                                            Set<Territorio> territorios, JanelaDeLicenca janela,
                                            Set<TipoDeDispositivo> dispositivos) {
        if (tenantId == null) {
            return Result.erro(new FalhaDeNegocio("LICENCA_SEM_TENANT",
                    "Licenca precisa pertencer a um tenant"));
        }
        if (titular == null || titular.isBlank()) {
            return Result.erro(new FalhaDeNegocio("LICENCA_SEM_TITULAR",
                    "Informe o titular dos direitos"));
        }
        if (referenciaDoContrato == null || referenciaDoContrato.isBlank()) {
            return Result.erro(new FalhaDeNegocio("LICENCA_SEM_CONTRATO",
                    "Informe a referencia do contrato ou da autorizacao"));
        }
        if (territorios == null || territorios.isEmpty()) {
            return Result.erro(new FalhaDeNegocio("LICENCA_SEM_TERRITORIO",
                    "Informe ao menos um territorio contratado"));
        }
        if (dispositivos == null || dispositivos.isEmpty()) {
            return Result.erro(new FalhaDeNegocio("LICENCA_SEM_DISPOSITIVO",
                    "Informe os dispositivos autorizados pelo contrato"));
        }
        if (janela == null) {
            return Result.erro(new FalhaDeNegocio("LICENCA_SEM_JANELA",
                    "Informe a janela contratada"));
        }
        return Result.ok(new Licenca(UUID.randomUUID(), tenantId, titular.trim(),
                referenciaDoContrato.trim(), territorios, janela, dispositivos));
    }

    /**
     * Anexar a comprovacao e o que promove a licenca a VIGENTE. Enquanto nao
     * houver documento, ela nao autoriza nada.
     */
    public Result<Licenca> anexarComprovacao(String uri) {
        if (uri == null || uri.isBlank()) {
            return Result.erro(new FalhaDeNegocio("COMPROVACAO_VAZIA",
                    "Anexe o contrato, a invoice ou a autorizacao por escrito"));
        }
        if (status == StatusDaLicenca.RESCINDIDA) {
            return Result.erro(new FalhaDeNegocio("LICENCA_RESCINDIDA",
                    "Licenca rescindida nao volta a vigorar; cadastre uma nova"));
        }
        this.comprovacaoUri = uri.trim();
        this.status = StatusDaLicenca.VIGENTE;
        return Result.ok(this);
    }

    public Result<Licenca> rescindir(String motivo) {
        if (status == StatusDaLicenca.RESCINDIDA) {
            return Result.erro(new FalhaDeNegocio("LICENCA_JA_RESCINDIDA",
                    "Licenca ja esta rescindida"));
        }
        this.status = StatusDaLicenca.RESCINDIDA;
        this.observacao = motivo;
        return Result.ok(this);
    }

    /**
     * Pergunta central do produto: esta licenca autoriza exibir para alguem
     * neste territorio, neste dispositivo, agora?
     */
    public boolean autoriza(Territorio territorioDoEspectador, TipoDeDispositivo dispositivo, Instant agora) {
        return vigenteEm(agora)
                && cobreTerritorio(territorioDoEspectador)
                && dispositivosAutorizados.contains(dispositivo);
    }

    /** Versao sem dispositivo, usada na decisao de publicar no catalogo. */
    public boolean vigenteEm(Instant agora) {
        return status == StatusDaLicenca.VIGENTE && janela.contem(agora);
    }

    public boolean cobreTerritorio(Territorio alvo) {
        return territorios.stream().anyMatch(t -> t.cobre(alvo));
    }

    public boolean venceEmAte(Instant agora, int dias) {
        long faltam = janela.diasAteVencer(agora);
        return faltam >= 0 && faltam <= dias;
    }

    public UUID id() { return id; }

    public UUID tenantId() { return tenantId; }

    public String titular() { return titular; }

    public String referenciaDoContrato() { return referenciaDoContrato; }

    public Set<Territorio> territorios() { return Collections.unmodifiableSet(territorios); }

    public JanelaDeLicenca janela() { return janela; }

    public Set<TipoDeDispositivo> dispositivosAutorizados() {
        return Collections.unmodifiableSet(dispositivosAutorizados);
    }

    public String comprovacaoUri() { return comprovacaoUri; }

    public StatusDaLicenca status() { return status; }

    public String observacao() { return observacao; }

    /** Reconstrucao a partir do banco, sem revalidar o que ja passou pela entrada. */
    public static Licenca reconstituir(UUID id, UUID tenantId, String titular, String referenciaDoContrato,
                                       Set<Territorio> territorios, JanelaDeLicenca janela,
                                       Set<TipoDeDispositivo> dispositivos, String comprovacaoUri,
                                       StatusDaLicenca status, String observacao) {
        var licenca = new Licenca(id, tenantId, titular, referenciaDoContrato, territorios, janela, dispositivos);
        licenca.comprovacaoUri = comprovacaoUri;
        licenca.status = status;
        licenca.observacao = observacao;
        return licenca;
    }
}
