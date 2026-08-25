package br.com.mirante.application.usecases.rights;

import br.com.mirante.application.Auditor;
import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.audit.AcaoAuditavel;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Rescisao com efeito imediato: tira do ar, na mesma transacao, tudo que
 * dependia daquela licenca. Esperar o job noturno seria manter conteudo sem
 * direito no ar por horas, que e exatamente o que a operacao precisa evitar.
 */
public class RescindirLicenca {

    private final Repositorios.DeLicenca licencas;
    private final Repositorios.DeTitulo titulos;
    private final Repositorios.DeCanal canais;
    private final Auditor auditor;
    private final Clock relogio;

    public RescindirLicenca(Repositorios.DeLicenca licencas, Repositorios.DeTitulo titulos,
                            Repositorios.DeCanal canais, Auditor auditor, Clock relogio) {
        this.licencas = licencas;
        this.titulos = titulos;
        this.canais = canais;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record Saida(UUID licencaId, int titulosBloqueados, int canaisTirados) {
    }

    public Result<Saida> executar(ContextoDoChamador chamador, UUID licencaId, String motivo) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("rescindir licenca"));
        }
        var achada = licencas.porId(chamador.tenantId(), licencaId);
        if (achada.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Licenca"));
        }
        var licenca = achada.get();
        var rescisao = licenca.rescindir(motivo);
        if (rescisao.falhou()) {
            return Result.erro(rescisao.falha().orElseThrow());
        }
        licencas.salvar(licenca);

        var agora = relogio.instant();
        int bloqueados = 0;
        for (var titulo : titulos.porLicenca(chamador.tenantId(), licencaId)) {
            if (titulo.revisarDireitos(licenca, agora)) {
                titulos.salvar(titulo);
                bloqueados++;
                auditor.registrar(chamador, AcaoAuditavel.TITULO_BLOQUEADO_POR_DIREITO, "titulo",
                        titulo.id().toString(), Map.of("motivo", "licenca rescindida"));
            }
        }

        int tirados = 0;
        for (var canal : canais.porLicenca(chamador.tenantId(), licencaId)) {
            if (canal.revisarDireitos(licenca, agora)) {
                canais.salvar(canal);
                tirados++;
                auditor.registrar(chamador, AcaoAuditavel.CANAL_FORA_DO_AR, "canal",
                        canal.id().toString(), Map.of("motivo", "licenca rescindida"));
            }
        }

        auditor.registrar(chamador, AcaoAuditavel.LICENCA_RESCINDIDA, "licenca", licencaId.toString(),
                Map.of("motivo", motivo == null ? "" : motivo,
                        "titulosBloqueados", String.valueOf(bloqueados),
                        "canaisTirados", String.valueOf(tirados)));

        return Result.ok(new Saida(licencaId, bloqueados, tirados));
    }
}
