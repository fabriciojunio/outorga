package br.com.outorga.application.usecases.rights;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.rights.Licenca;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Varredura de direitos. Roda de hora em hora e faz uma coisa só: comparar o
 * que está no ar com o que tem licença vigente agora, e acertar a diferença
 * nos dois sentidos.
 *
 * E o que sustenta a promessa comercial do produto. Sem está rotina, "gate de
 * conteúdo" e uma frase no contrato; com ela, e um comportamento observavel.
 */
public class RevisarDireitosVigentes {

    private final Repositorios.DeTenant tenants;
    private final Repositorios.DeTitulo titulos;
    private final Repositorios.DeCanal canais;
    private final Repositorios.DeLicenca licencas;
    private final Auditor auditor;
    private final Clock relogio;

    public RevisarDireitosVigentes(Repositorios.DeTenant tenants, Repositorios.DeTitulo titulos,
                                   Repositorios.DeCanal canais, Repositorios.DeLicenca licencas,
                                   Auditor auditor, Clock relogio) {
        this.tenants = tenants;
        this.titulos = titulos;
        this.canais = canais;
        this.licencas = licencas;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public record Resultado(int titulosBloqueados, int titulosLiberados, int canaisAfetados) {

        public boolean houveMudanca() {
            return titulosBloqueados + titulosLiberados + canaisAfetados > 0;
        }
    }

    public Resultado executar() {
        var agora = relogio.instant();
        int bloqueados = 0;
        int liberados = 0;
        int canaisAfetados = 0;

        for (var tenant : tenants.todos()) {
            var chamador = ContextoDoChamador.doSistema(tenant.id());
            Map<UUID, Licenca> cache = new HashMap<>();

            for (var titulo : titulos.sujeitosARevisaoDeDireitos(tenant.id())) {
                var licenca = carregar(cache, tenant.id(), titulo.licencaId());
                boolean estavaNoAr = titulo.noAr();
                if (titulo.revisarDireitos(licenca, agora)) {
                    titulos.salvar(titulo);
                    if (estavaNoAr) {
                        bloqueados++;
                        auditor.registrar(chamador, AcaoAuditavel.TITULO_BLOQUEADO_POR_DIREITO,
                                "titulo", titulo.id().toString(),
                                Map.of("motivo", String.valueOf(titulo.motivoDoBloqueio())));
                    } else {
                        liberados++;
                        auditor.registrar(chamador, AcaoAuditavel.TITULO_PUBLICADO, "titulo",
                                titulo.id().toString(), Map.of("motivo", "licença voltou a vigorar"));
                    }
                }
            }

            for (var canal : canais.doTenant(tenant.id())) {
                var licenca = carregar(cache, tenant.id(), canal.licencaId());
                boolean estavaNoAr = canal.noAr();
                if (canal.revisarDireitos(licenca, agora)) {
                    canais.salvar(canal);
                    canaisAfetados++;
                    auditor.registrar(chamador,
                            estavaNoAr ? AcaoAuditavel.CANAL_FORA_DO_AR : AcaoAuditavel.CANAL_NO_AR,
                            "canal", canal.id().toString(), Map.of());
                }
            }
        }

        return new Resultado(bloqueados, liberados, canaisAfetados);
    }

    private Licenca carregar(Map<UUID, Licenca> cache, UUID tenantId, UUID licencaId) {
        if (licencaId == null) {
            return null;
        }
        return cache.computeIfAbsent(licencaId,
                id -> licencas.porId(tenantId, id).orElse(null));
    }
}
