package br.com.outorga.application.usecases.rights;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Rescisão com efeito imediato: tira do ar, na mesma transação, tudo que
 * dependia daquela licença. Esperar o job noturno seria manter conteúdo sem
 * direito no ar por horas, que e exatamente o que a operação precisa evitar.
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
            return Result.erro(Falhas.semPermissao("rescindir licença"));
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
                        titulo.id().toString(), Map.of("motivo", "licença rescindida"));
            }
        }

        int tirados = 0;
        for (var canal : canais.porLicenca(chamador.tenantId(), licencaId)) {
            if (canal.revisarDireitos(licenca, agora)) {
                canais.salvar(canal);
                tirados++;
                auditor.registrar(chamador, AcaoAuditavel.CANAL_FORA_DO_AR, "canal",
                        canal.id().toString(), Map.of("motivo", "licença rescindida"));
            }
        }

        auditor.registrar(chamador, AcaoAuditavel.LICENCA_RESCINDIDA, "licenca", licencaId.toString(),
                Map.of("motivo", motivo == null ? "" : motivo,
                        "titulosBloqueados", String.valueOf(bloqueados),
                        "canaisTirados", String.valueOf(tirados)));

        return Result.ok(new Saida(licencaId, bloqueados, tirados));
    }
}
