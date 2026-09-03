package br.com.outorga.application.usecases.rights;

import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.rights.Licenca;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Painel de vencimento. Renovação de licença leva semanas para ser assinada,
 * então avisar no dia do vencimento não serve de nada: o padrão aqui e olhar
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
            return Result.erro(Falhas.semPermissao("consultar licenças"));
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

    /** Usado pelo alerta automático, sem contexto de usuário. */
    public List<Licenca> vencendoEm(int dias) {
        return licencas.vencendoAte(relogio.instant().plus(dias, ChronoUnit.DAYS));
    }
}
