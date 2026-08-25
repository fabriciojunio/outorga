package br.com.mirante.application.usecases.rights;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.domain.rights.JanelaDeLicenca;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.domain.rights.Territorio;
import br.com.mirante.domain.rights.TipoDeDispositivo;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class CadastrarLicenca {

    private final Repositorios.DeLicenca licencas;
    private final Auditor auditor;

    public CadastrarLicenca(Repositorios.DeLicenca licencas, Auditor auditor) {
        this.licencas = licencas;
        this.auditor = auditor;
    }

    public record Entrada(String titular, String referenciaDoContrato, Set<String> territorios,
                          Instant inicio, Instant fim, Set<TipoDeDispositivo> dispositivos,
                          String comprovacaoUri) {
    }

    public Result<Licenca> executar(ContextoDoChamador chamador, Entrada entrada) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("cadastrar licenca"));
        }

        var territorios = new LinkedHashSet<Territorio>();
        for (String codigo : entrada.territorios()) {
            var territorio = Territorio.de(codigo);
            if (territorio.falhou()) {
                return Result.erro(territorio.falha().orElseThrow());
            }
            territorios.add(territorio.valorOuFalha());
        }

        JanelaDeLicenca janela;
        try {
            janela = new JanelaDeLicenca(entrada.inicio(), entrada.fim());
        } catch (IllegalArgumentException e) {
            return Result.erro(Falhas.invalido(e.getMessage()));
        }

        var criada = Licenca.cadastrar(chamador.tenantId(), entrada.titular(),
                entrada.referenciaDoContrato(), territorios, janela, entrada.dispositivos());
        if (criada.falhou()) {
            return criada;
        }

        var licenca = criada.valorOuFalha();

        // Cadastro com comprovacao ja em maos vira VIGENTE de uma vez; sem
        // comprovacao a licenca nasce em rascunho e nao publica nada.
        if (entrada.comprovacaoUri() != null && !entrada.comprovacaoUri().isBlank()) {
            var anexo = licenca.anexarComprovacao(entrada.comprovacaoUri());
            if (anexo.falhou()) {
                return anexo;
            }
        }

        licencas.salvar(licenca);
        auditor.registrar(chamador, AcaoAuditavel.LICENCA_CADASTRADA, "licenca",
                licenca.id().toString(),
                Map.of("titular", licenca.titular(),
                        "contrato", licenca.referenciaDoContrato(),
                        "status", licenca.status().name()));
        return Result.ok(licenca);
    }
}
