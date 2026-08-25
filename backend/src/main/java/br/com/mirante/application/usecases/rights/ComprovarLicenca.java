package br.com.mirante.application.usecases.rights;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.util.Map;
import java.util.UUID;

/**
 * Anexa a comprovacao e coloca a licenca em vigencia. E o momento em que o
 * conteudo dela passa a valer para publicacao.
 */
public class ComprovarLicenca {

    private final Repositorios.DeLicenca licencas;
    private final Auditor auditor;

    public ComprovarLicenca(Repositorios.DeLicenca licencas, Auditor auditor) {
        this.licencas = licencas;
        this.auditor = auditor;
    }

    public Result<Licenca> executar(ContextoDoChamador chamador, UUID licencaId, String comprovacaoUri) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("comprovar licenca"));
        }
        var achada = licencas.porId(chamador.tenantId(), licencaId);
        if (achada.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Licenca"));
        }
        var licenca = achada.get();
        var anexo = licenca.anexarComprovacao(comprovacaoUri);
        if (anexo.falhou()) {
            return anexo;
        }
        licencas.salvar(licenca);
        auditor.registrar(chamador, AcaoAuditavel.LICENCA_COMPROVADA, "licenca",
                licenca.id().toString(), Map.of("comprovacao", comprovacaoUri));
        return Result.ok(licenca);
    }
}
