package br.com.mirante.application.usecases.rights;

import br.com.mirante.application.ContextoDoChamador;
import br.com.mirante.application.ports.Repositorios;
import br.com.mirante.domain.rights.Licenca;
import br.com.mirante.shared.Falhas;
import br.com.mirante.shared.Result;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Painel de vencimento. Renovacao de licenca leva semanas para ser assinada,
 * entao avisar no dia do vencimento nao serve de nada: o padrao aqui e olhar
 * 60 dias para frente.
 */
public class ListarLicencasAVencer {

    public static final int DIAS_PADRAO = 60;

    private final Repositorios.DeLicenca licencas;
    private final Repositorios.DeTitulo titulos;
    private final Clock relogio;

    public ListarLicencasAVencer(Repositorios.DeLicenca licencas, Repositorios.DeTitulo titulos,
                                 Clock relogio) {
        this.licencas = licencas;
        this.titulos = titulos;
        this.relogio = relogio;
    }

    public record Item(UUID licencaId, String titular, String contrato, long diasRestantes,
                       int titulosAfetados) {
    }

    public Result<List<Item>> executar(ContextoDoChamador chamador, int dias) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("consultar licencas"));
        }
        var agora = relogio.instant();
        var janela = dias <= 0 ? DIAS_PADRAO : dias;

        var itens = licencas.doTenant(chamador.tenantId()).stream()
                .filter(licenca -> licenca.venceEmAte(agora, janela))
                .map(licenca -> new Item(
                        licenca.id(),
                        licenca.titular(),
                        licenca.referenciaDoContrato(),
                        licenca.janela().diasAteVencer(agora),
                        titulos.porLicenca(chamador.tenantId(), licenca.id()).size()))
                .sorted(Comparator.comparingLong(Item::diasRestantes))
                .toList();

        return Result.ok(itens);
    }

    /** Usado pelo alerta automatico, sem contexto de usuario. */
    public List<Licenca> vencendoEm(int dias) {
        return licencas.vencendoAte(relogio.instant().plus(dias, ChronoUnit.DAYS));
    }
}
