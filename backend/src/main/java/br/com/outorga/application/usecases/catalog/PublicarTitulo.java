package br.com.outorga.application.usecases.catalog;

import br.com.outorga.application.Auditor;
import br.com.outorga.application.ContextoDoChamador;
import br.com.outorga.application.ports.Repositorios;
import br.com.outorga.domain.audit.AcaoAuditavel;
import br.com.outorga.domain.catalog.Titulo;
import br.com.outorga.shared.Falhas;
import br.com.outorga.shared.Result;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Publicar exige informar qual licença autoriza aquele título. Não ha
 * publicação sem licença no sistema, nem por atalho de administrador: o
 * próprio ADMIN_PLATAFORMA passa por aqui.
 */
public class PublicarTitulo {

    private final Repositorios.DeTitulo titulos;
    private final Repositorios.DeLicenca licencas;
    private final Auditor auditor;
    private final Clock relogio;

    public PublicarTitulo(Repositorios.DeTitulo titulos, Repositorios.DeLicenca licencas,
                          Auditor auditor, Clock relogio) {
        this.titulos = titulos;
        this.licencas = licencas;
        this.auditor = auditor;
        this.relogio = relogio;
    }

    public Result<Titulo> executar(ContextoDoChamador chamador, UUID tituloId, UUID licencaId) {
        if (!chamador.podePublicarCatalogo()) {
            return Result.erro(Falhas.semPermissao("publicar título"));
        }

        var achado = titulos.porId(chamador.tenantId(), tituloId);
        if (achado.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Titulo"));
        }
        var licenca = licencas.porId(chamador.tenantId(), licencaId);
        if (licenca.isEmpty()) {
            return Result.erro(Falhas.naoEncontrado("Licenca"));
        }

        var titulo = achado.get();
        var publicacao = titulo.publicar(licenca.get(), relogio.instant());
        if (publicacao.falhou()) {
            return publicacao;
        }

        titulos.salvar(titulo);
        auditor.registrar(chamador, AcaoAuditavel.TITULO_PUBLICADO, "titulo", titulo.id().toString(),
                Map.of("licenca", licencaId.toString(),
                        "titular", licenca.get().titular(),
                        "contrato", licenca.get().referenciaDoContrato()));
        return Result.ok(titulo);
    }
}
